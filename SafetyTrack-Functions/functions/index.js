const functions = require('firebase-functions');
const admin = require('firebase-admin');
const nodemailer = require('nodemailer');

admin.initializeApp();

const transporter = nodemailer.createTransport({
    host: 'smtp.gmail.com',
    port: 465,
    secure: true,
    auth: {
        user: 'ashwithac22@gmail.com',
        pass: 'xratnnwipzxkgoou'
    }
});

// Send OTP Email
exports.sendOTPEmail = functions.https.onCall(async (data, context) => {
    const { email, otp } = data;
    
    if (!email || !otp) {
        throw new functions.https.HttpsError(
            'invalid-argument',
            'Email and OTP are required'
        );
    }

    // Check if user exists
    const usersRef = admin.firestore().collection('users');
    const snapshot = await usersRef.where('email', '==', email).limit(1).get();
    
    if (snapshot.empty) {
        throw new functions.https.HttpsError(
            'not-found',
            'Email not registered'
        );
    }

    const mailOptions = {
        from: '"SafetyTrack" <ashwithac22@gmail.com>',
        to: email,
        subject: 'SafetyTrack - Password Reset OTP',
        html: `
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 10px;">
                <h1 style="color: #3F51B5; text-align: center;">SafetyTrack</h1>
                <p style="font-size: 18px; text-align: center;">Password Reset Request</p>
                <div style="background-color: #3F51B5; color: white; font-size: 32px; font-weight: bold; text-align: center; padding: 20px; border-radius: 8px; letter-spacing: 8px; margin: 20px 0;">
                    ${otp}
                </div>
                <p>This OTP is valid for <strong>5 minutes</strong>.</p>
                <p style="color: #f44336; font-size: 12px;">⚠️ Never share this OTP with anyone. SafetyTrack will never ask for your OTP.</p>
                <hr style="border: none; border-top: 1px solid #e0e0e0; margin: 20px 0;">
                <p style="color: #666; font-size: 12px; text-align: center;">This is an automated message, please do not reply.</p>
            </div>
        `
    };

    try {
        await transporter.sendMail(mailOptions);
        console.log(`OTP sent to ${email}`);
        return { success: true };
    } catch (error) {
        console.error('Email error:', error);
        throw new functions.https.HttpsError(
            'internal',
            'Failed to send email. Please try again.'
        );
    }
});

// Store OTP
exports.storeOTP = functions.https.onCall(async (data, context) => {
    const { email, otp } = data;
    
    const otpData = {
        email,
        otp,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        expiresAt: new Date(Date.now() + 5 * 60 * 1000),
        verified: false
    };

    await admin.firestore().collection('otps').doc(email).set(otpData);
    return { success: true };
});

// Verify OTP
exports.verifyOTP = functions.https.onCall(async (data, context) => {
    const { email, otp } = data;
    
    const otpDoc = await admin.firestore().collection('otps').doc(email).get();
    
    if (!otpDoc.exists) {
        throw new functions.https.HttpsError('not-found', 'No OTP request found');
    }

    const otpData = otpDoc.data();
    const now = new Date();
    const expiresAt = otpData.expiresAt.toDate();

    if (now > expiresAt) {
        await admin.firestore().collection('otps').doc(email).delete();
        throw new functions.https.HttpsError('deadline-exceeded', 'OTP expired');
    }

    if (otpData.otp !== otp) {
        throw new functions.https.HttpsError('invalid-argument', 'Invalid OTP');
    }

    await admin.firestore().collection('otps').doc(email).update({
        verified: true,
        verifiedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return { success: true };
});