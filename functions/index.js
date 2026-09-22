const functions = require("firebase-functions");
const admin = require("firebase-admin");

// Initialize Firebase Admin SDK
admin.initializeApp({
  databaseURL: "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/",
});

const db = admin.database();

// Valid alert types enum
const VALID_ALERT_TYPES = ["PANIC", "CHECKIN", "LOW_BATTERY", "TRACKING_UPDATE"];

/**
 * Cloud Function to ingest alerts from GuardBand devices
 * Deployed to asia-southeast1 to match Realtime Database region
 */
exports.ingestAlert = functions
    .region("asia-southeast1")
    .https
    .onRequest(async (req, res) => {
      // CORS headers for cross-origin requests
      res.set("Access-Control-Allow-Origin", "*");
      res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
      res.set("Access-Control-Allow-Headers", "Content-Type");

      // Handle OPTIONS preflight request
      if (req.method === "OPTIONS") {
        res.status(204).send("");
        return;
      }

      // Reject non-POST requests
      if (req.method !== "POST") {
        res.status(405).json({
          error: "Method not allowed",
          message: "Only POST requests are accepted",
        });
        return;
      }

      const payload = req.body;

      // Validate required top-level fields
      const requiredFields = [
        "schemaVersion",
        "deviceId",
        "type",
        "timestamp",
        "battery",
        "sequenceId",
      ];

      const missingFields = [];
      for (const field of requiredFields) {
        if (!payload.hasOwnProperty(field) || payload[field] === null || payload[field] === undefined) {
          missingFields.push(field);
        }
      }

      // Also validate nested battery fields
      if (payload.battery) {
        if (!payload.battery.hasOwnProperty("percent")) {
          missingFields.push("battery.percent");
        }
        if (!payload.battery.hasOwnProperty("isCharging")) {
          missingFields.push("battery.isCharging");
        }
      }

      // Check for missing fields
      if (missingFields.length > 0) {
        res.status(400).json({
          error: "Validation failed",
          message: "Missing required fields",
          missingFields: missingFields,
        });
        return;
      }

      // Validate alert type enum
      if (!VALID_ALERT_TYPES.includes(payload.type)) {
        res.status(400).json({
          error: "Validation failed",
          message: `Invalid alert type. Must be one of: ${VALID_ALERT_TYPES.join(", ")}`,
          receivedType: payload.type,
        });
        return;
      }

      // Optional but recommended: validate location fields if present
      // Note: Schema marks location as required, but requirements mention uncertainty
      // I'll validate if present but won't reject if missing (flagging this in report)
      if (payload.location) {
        const locationFields = ["lat", "lng", "accuracyMeters"];
        const missingLocationFields = [];
        for (const field of locationFields) {
          if (!payload.location.hasOwnProperty(field)) {
            missingLocationFields.push(`location.${field}`);
          }
        }
        if (missingLocationFields.length > 0) {
          res.status(400).json({
            error: "Validation failed",
            message: "Incomplete location data",
            missingFields: missingLocationFields,
          });
          return;
        }
      }

      // Write to Realtime Database
      try {
        const deviceId = payload.deviceId;
        const sequenceId = payload.sequenceId;

        // Write to both paths simultaneously
        const updates = {};
        updates[`/devices/${deviceId}/latest`] = payload;
        updates[`/devices/${deviceId}/history/${sequenceId}`] = payload;

        await db.ref().update(updates);

        // Success response
        res.status(200).json({
          success: true,
          message: "Alert ingested successfully",
          deviceId: deviceId,
          sequenceId: sequenceId,
          type: payload.type,
          timestamp: payload.timestamp,
        });

        // Log successful ingestion
        console.log(`Alert ingested: device=${deviceId}, type=${payload.type}, seq=${sequenceId}`);
      } catch (error) {
        console.error("Database write failed:", error);
        res.status(500).json({
          error: "Internal server error",
          message: "Failed to write alert to database",
        });
      }
    });
