# Handover

## Priorities
I prioritized making the appointment type affect the whole booking journey: availability, conflict checking, booking validation, and the information shown to patients. This addresses the core Scenario B behavior while keeping the existing questionnaire and API flow.

## Feature decision
I chose a 15-minute candidate grid and stored each in-memory booking as a half-open interval assigned to a clinician shift. That keeps the scheduler small while supporting 15-, 30-, and 45-minute appointments, break boundaries, and back-to-back bookings. I rejected a general interval-scheduling framework as unnecessary for this single-clinic POC.

## Deliberately unfinished
Bookings are in-memory and synchronization protects only this application process; there is no persistence or cross-instance coordination. Cancellation/rescheduling, multiple clinics, and a dedicated frontend test framework are outside this feature slice.

## Verification
- `cd backend && ./gradlew test --console=plain` — `BUILD SUCCESSFUL`; 20 tests, 0 failures, 0 errors (Gradle reported tasks up-to-date).
- `cd frontend && npm run build` — passed TypeScript checks and Vite production build.

The frontend has no existing test or lint script, so neither was run.
