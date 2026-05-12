# Grameen-Light Android App

Grameen-Light is a base internship project for a citizen-led village streetlight audit.

## Features

- Simulated village pole map with color-coded pole status dots.
- One-tap report flow: Working, Fused, or Burning in Day.
- Unique complaint ID generation for every report, including repeated reports on the same pole.
- If the same active issue is reported again before it is cleared, the app shows the existing complaint instead of creating a duplicate.
- Dot colors update immediately from citizen observations. Backend `FIXED` still controls when a new complaint can be created for an already-active issue.
- Repair tracker with Reported, Assigned, and Fixed states from Firebase/backend.
- Total Energy Saved This Month visual. It starts from a 12-pole monthly baseline, subtracts active daytime-burning poles, and keeps actual repair-delay waste deducted after a complaint is fixed.
- Firebase Firestore sync for shared village status.
- Room database for persistent local/offline records.
- Automatic day/night audit theme based on the phone time.

## How to Run

1. Copy this full folder into a new empty directory.
2. Open the folder in Android Studio.
3. Let Gradle sync download dependencies.
4. Run the `app` configuration on an emulator or Android phone.

The app package is:

```text
com.grameenlight.app
```

Your Firebase config is already placed at:

```text
app/google-services.json
```

## Firebase Collections

The app writes to these Firestore collections:

```text
poleReports/{complaintId}
poleStatuses/{poleId}
```

Citizen users can only create reports. They cannot mark reports as Assigned or Fixed from the app.

To update a complaint from the backend/Firebase Console:

1. Open `poleReports/{complaintId}`.
2. Change `repairState` to `ASSIGNED` or `FIXED`.
3. When marking fixed, also set `fixedAtMillis` to the current epoch time in milliseconds.
4. Open `poleStatuses/{poleId}` for that same pole.
5. Change `repairState` to the same value.
6. If the complaint is fixed, also set `status` to `WORKING`.

The app listens to Firebase and updates the tracker/map automatically.

## Reset Demo Data

To clear the backend before a demo, open Google Cloud Shell for project `grameenlight-a1898`, upload or paste:

```text
tools/reset_firestore.sh
```

Then run:

```bash
bash reset_firestore.sh
```

It deletes all `poleReports` and `poleStatuses`, then recreates 12 green `poleStatuses` documents.

For a college demo, enable Firestore in Firebase Console. If your Firestore rules block writes, reports will still save locally in Room DB, but cloud sync will show as `Local`.

Testing-only Firestore rule:

```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

Use stricter authenticated rules before any real public deployment.

## Requirement Checklist

- Pole status visible as color-coded dot on map: done.
- Complaint ID generated for every report: done. The ID includes milliseconds and a UUID suffix, so the same pole can be reported again after repair.
- Re-report flow: done. A yellow pole can be marked Working/green, then reported Burning in Day/yellow again with a new complaint.
- Active complaint guard: done. Before backend `FIXED`, changing the dot back to red/orange shows `Complaint already active` and does not create duplicate complaint documents.
- Extremely simple one-tap reporting: done.
- Energy goal visual: done. Baseline is `12 poles * 40W * 12 hours * 30 days = 172.8 kWh`; each active daytime-burning pole subtracts `14.4 kWh`.
- Repair-delay waste: done. A fixed daytime-burning complaint keeps its real wasted energy deducted, for example `40W * 0.5 hour = 0.02 kWh`.
- Firebase sync: done.
- Room DB local persistence: done.
- Day/night audit theme from phone time: done.
- Backend-only repair status updates: done.
