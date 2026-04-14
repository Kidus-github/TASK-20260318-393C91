## I. Authentication & Security

### 1. Password Reset in Air-Gapped Environments

**Question:** Since there is no external email or SMS service, how does a user recover a forgotten password?
**Assumption:** Users cannot self-reset via traditional methods; intervention is required to maintain security.
**Solution:** Implement a **"Security Question"** secondary authentication factor during registration. If failed, the system generates a **unique Request Token** that the user must physically provide to an Administrator to receive a temporary one-time password (OTP).

### 2. Session Hijacking & Token Replay

**Question:** In a shared LAN (e.g., transit station Wi-Fi), how do we prevent an attacker from intercepting and replaying a JWT?
**Assumption:** The LAN is "trusted" but potentially compromised by unauthorized devices.
**Solution:** Implement **Fingerprinted JWTs**. Bind the token to the browser’s `User-Agent` and a partial `IP address`. If the request fingerprint changes mid-session, the server revokes the token and forces re-authentication.

### 3. Password Salt Strategy & Rotation

**Question:** The spec mentions "salted hashing," but what is the salt’s scope and the hashing algorithm’s complexity?
**Assumption:** A global salt is too weak; per-user salts are necessary for modern security.
**Solution:** Use **Argon2id** (superior to BCrypt for modern side-channel resistance) with a **random 16-byte salt per user**. Implement a "Work Factor" that is stored in the config, allowing Admins to increase hashing difficulty as hardware evolves.

---

## II. Data Storage & Encryption

### 4. Versioning Collision in Stop Structures

**Question:** When tracking "changes in stop structures," what happens if two admins upload different JSON templates for the same stop simultaneously?
**Assumption:** Last-in-wins is unacceptable for transit safety and data integrity.
**Solution:** Use a **Semantic Versioning (Major.Minor.Patch)** system for data templates. Implement a **Pre-commit Check** that compares the incoming schema hash against the current active version; if a conflict exists, the system forces a "Manual Merge" workflow for the Administrator.

### 5. Disk Space Exhaustion on Local LAN

**Question:** With structured logging, trace IDs, and local backups, how does the system prevent the PostgreSQL disk from filling up, crashing the OS?
**Assumption:** Logs and backups will grow indefinitely if not managed.
**Solution:** Implement an **Automated Data Retention Policy (ADRP)**. Move logs older than 30 days and backups older than 7 days to a separate "Archive" partition/mount. If the main partition reaches 90% capacity, the system must trigger a "Critical Alert" and halt non-essential logging.

### 6. Sensitivity-Based Data Masking at Rest

**Question:** "Message content is desensitized," but is this done at the UI layer or the Database layer?
**Assumption:** Storing raw sensitive data in plain text violates security best practices, even in a LAN.
**Solution:** Implement **Column-Level Encryption (CLE)** in PostgreSQL for fields marked with high sensitivity. The Spring Boot application will use an **AES-256 GCM** encryption provider to mask data before it hits the disk, only decrypting it for authorized roles.

---

## III. State Management & Lifecycle

### 7. Workflow Engine "Zombies"

**Question:** If a dispatcher starts an approval but their session expires or the browser crashes, what happens to the locked "Task"?
**Assumption:** Tasks could remain "In Progress" indefinitely, blocking other dispatchers.
**Solution:** Implement **Lease-based Task Locking**. When a dispatcher opens a task, they acquire a 15-minute lease. The Angular frontend must "heartbeat" to renew the lease. If the heartbeat stops, the task is automatically unlocked and returned to the pool.

### 8. Message Center Read-State Synchronization

**Question:** If a passenger opens the app in two different browser tabs, how is the "Read/Unread" count synchronized across tabs?
**Assumption:** Users expect a consistent state without refreshing the page.
**Solution:** Use the **BroadcastChannel API** in Angular to sync state locally between tabs. When a message is marked as read in Tab A, it emits an event that Tab B listens for, updating the UI count instantly without a redundant server call.

### 9. Arrival Reminder Lifecycle

**Question:** What happens to an "Upcoming Reminder" (10m advance) if the bus route is cancelled or significantly delayed _after_ the reminder is set?
**Assumption:** Stale reminders lead to "Missed Check-ins" and user frustration.
**Solution:** Reminders must be **Dynamic Subscriptions**, not static timers. The scheduling task must re-validate the bus's "Estimated Time of Arrival" (ETA) every 60 seconds. If the ETA shifts significantly, the reminder time is recalculated and a "Delay Updated" notification is pushed.

---

## IV. Concurrency & Multi-user Behavior

### 10. Search Result Race Conditions

**Question:** While a user is typing (Autocomplete), if a high-priority data update is committed to the DB, could the user see inconsistent search results?
**Assumption:** Data consistency is more important than millisecond-level search speed.
**Solution:** Use **PostgreSQL Read Committed** isolation level combined with **RxJS `switchMap`** in Angular. This ensures that if a new search is triggered before the previous one finishes, the previous (now stale) request is cancelled immediately.

### 11. Parallel Approval Conflicts

**Question:** In "Joint Approvals," what is the system behavior if Dispatcher A approves and Dispatcher B rejects at the exact same millisecond?
**Assumption:** A "Reject" should always take precedence for safety.
**Solution:** Implement an **Atomic Transaction** in the workflow service. Use a "State Transition Matrix" where `REJECT` is a terminal state. If two requests hit the server, the one that acquires the DB row lock first sets the state; the second request must validate the new state and fail gracefully if it is no longer "Pending."

---

## V. Offline Behavior & Sync

### 12. Clock Skew Between LAN Nodes

**Question:** If the Server is at 10:00 and the Passenger's laptop is at 10:05, the 22:00 "Do Not Disturb" (DND) logic will fail.
**Assumption:** We cannot rely on the client's local system clock.
**Solution:** On application load, the server sends its current timestamp. The Angular app calculates the **Delta ($\Delta$)** between local and server time. All time-sensitive UI logic (DND, Reminders) must use `LocalTime + \Delta`.

### 13. Service Worker Cache Invalidation

**Question:** Since the platform is offline, Angular assets are cached. How do we push an urgent UI update (e.g., a new emergency field) to all users?
**Assumption:** Users might keep the app open for weeks without a hard refresh.
**Solution:** Implement a **Version Check Endpoint** that the Angular app polls every 5 minutes. If the server version > client version, the app displays a non-intrusive "Update Available" banner that performs a `window.location.reload()` upon user confirmation.

---

## VI. Error Handling & Recovery

### 14. Message Queue Poison Pills

**Question:** If a specific notification template is corrupted and causes the "Scheduled Task" to crash, how do we prevent it from blocking all other notifications?
**Assumption:** One bad message should not break the whole system.
**Solution:** Implement a **Dead Letter Queue (DLQ)** logic. If a message fails to process 3 times, it is moved to a `failed_messages` table with a stack trace. The system then moves to the next message in the queue automatically.

### 15. Partial Data Parsing Failure

**Question:** If an HTML template changes and the parser can find the "Stop Name" but not the "Price," does it discard the whole record?
**Assumption:** Partial data is better than no data, as long as it's flagged.
**Solution:** Implement **Graceful Degradation** in the ETL pipeline. The record is saved with the missing field as `NULL`, but the record status is set to `WARNING`. A notification is sent to the Administrator with a link to the specific "Source Log" for manual correction.

---

## VII. Performance & Limits

### 16. Autocomplete Under High Load

**Question:** As the number of stops grows (e.g., 50,000+), how do we keep search latency under 500ms for P95?
**Assumption:** Database-only `LIKE` queries will eventually fail the 500ms requirement.
**Solution:** Implement a **PostgreSQL GIN (Generalized Inverted Index)** with `pg_trgm` for fuzzy matching. For Pinyin, store a pre-computed `search_vector` column. If performance still lags, use an in-memory **Caffeine Cache** in Spring Boot for the top 1,000 most popular search terms.

### 17. Batch Processing Memory Spikes

**Question:** If a dispatcher triggers a "Batch Approval" for 5,000 routes, how do we prevent a Java `OutOfMemoryError`?
**Assumption:** Processing everything in one big list is dangerous.
**Solution:** Use **Spring Batch** or **StatelessSession** in Hibernate to stream records from the database in chunks (e.g., 100 at a time). This keeps the memory footprint constant regardless of batch size.

---

## VIII. Edge Cases & UX Constraints

### 18. The "Midnight DND" Edge Case

**Question:** If a user sets a reminder for a bus arriving at 07:05, but the DND period ends at 07:00, and the 10-minute reminder should have fired at 06:55 (during DND), what happens?
**Assumption:** Users would rather be woken up 5 minutes late than not at all.
**Solution:** Implement **Suppressed Notification Catch-up**. When the DND period ends at 07:00, the system checks for any reminders that "fired" during the DND window within the last 15 minutes and delivers them immediately as a batch.
