# Volcano Arts Center — Quick Start Guide

### 1. Project Directory
```powershell
cd "C:\Users\Badaga\Desktop\Musanze\VolcanoArtsCenter-main 2\VolcanoArtsCenter-main"
```

### 2. How to Start the System
Open PowerShell in this folder and run:
```powershell
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

### 2. Access Links
*   **Website:** [http://localhost:8080](http://localhost:8080)
*   **Admin Dashboard:** [http://localhost:8080/internal/login](http://localhost:8080/internal/login)

### 3. Test Credentials
| Role | Email | Password |
| :--- | :--- | :--- |
| **Super Admin** | `admin1@volcanoartscenter.rw` | `SuperAdmin!2026` |
| **Operations** | `admin2@volcanoartscenter.rw` | `OpsManager!2026` |
| **Content Manager** | `admin3@volcanoartscenter.rw` | `ContentManager!2026` |
| **Client** | `client1@volcanoartscenter.rw` | `RegisteredClient!2026` |

### 4. Recent Fixes & Features
*   **Redis Bypassed:** System runs without Redis using `-Dspring.session.store-type=none`.
*   **Database:** Connected to PostgreSQL `volcano_platform` using password `1930`.
*   **Review System:** Users can submit reviews on the homepage; Admins must approve them in the dashboard.
*   **Account Activation:** Admins can now re-activate users from the "Staff & Accounts" table.
