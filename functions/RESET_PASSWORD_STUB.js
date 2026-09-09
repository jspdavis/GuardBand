/**
 * Optional Cloud Function for in-app password reset after OTP verification.
 * Deploy with Firebase Blaze + `firebase deploy --only functions`.
 *
 * exports.resetPasswordWithOtp = functions.https.onCall(async (data, context) => {
 *   const email = (data.email || "").toLowerCase().trim();
 *   const newPassword = data.newPassword || "";
 *   if (!email || newPassword.length < 8) {
 *     throw new functions.https.HttpsError("invalid-argument", "Invalid payload");
 *   }
 *   const user = await admin.auth().getUserByEmail(email);
 *   await admin.auth().updateUser(user.uid, { password: newPassword });
 *   return { ok: true };
 * });
 */
