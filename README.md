# GPU Utilization Management

A Java Swing application for managing student GPU allowances and simulating
FCFS scheduling on four identical GPUs. GPU execution is simulated; workload
units are not NVIDIA H100 performance measurements.

## Features

- Student registration with administrator approval and account balances.
- Admin allowance adjustments with recorded reasons.
- Student job submission, history search, and cancellation.
- One FCFS queue, deterministic GPU assignment, minute-by-minute simulation,
  predicted starts/finishes, wait times, and utilization.
- Completed and cancelled job history, including runtime charges and reasons.
- Separate account and scheduler snapshots. The simulation pauses while the
  application is closed; the saved clock and running jobs resume on restart.
- Initial simulation date and time are selected with Swing date/time controls.

The predefined administrator credentials are `admin` / `gpu2026`. Each
approved student signs in using their university ID and chosen password.

## Requirements

- JDK 17 or newer.
- Windows PowerShell, Command Prompt, or another terminal.

## Build and run (PowerShell)

From the project directory:

```powershell
New-Item -ItemType Directory -Force out
javac -encoding UTF-8 -d out (Get-ChildItem -Path src -Recurse -Filter *.java | ForEach-Object FullName)
java -cp out gpu.accounts.Main
```

The first launch asks for an initial simulation date and time. By default,
the program stores account data in `data/accounts.dat` and the simulation in
`data/simulation.dat`. Pass alternative paths as the first and second
arguments:

```powershell
java -cp out gpu.accounts.Main data/accounts-demo.dat data/simulation-demo.dat
```

## Sample demonstration data

The sample generator refuses to overwrite either output file. Run it with
fresh paths:

```powershell
java -cp out gpu.scheduling.CreateDemoData
```

It creates five approved students, one pending request, one rejected request,
and 20 allowance-backed jobs across queued, running, completed, and cancelled
states. The sample credentials are:

- Administrator: `admin` / `gpu2026`
- Students `S1` through `S5`: `student123`

Open the generated data with:

```powershell
java -cp out gpu.accounts.Main data/demo-accounts.dat data/demo-simulation.dat
```

## Tests

The project includes dependency-free behavioral checks:

```powershell
java -cp out gpu.scheduling.SchedulerTest
java -cp out gpu.scheduling.JobServiceTest
```

They cover FCFS ordering, GPU tie-breaking, wait predictions, completion,
cancellation charges, utilization, allowance settlement, access scoping, and
snapshot recovery.

## Design and class relationships

```mermaid
classDiagram
    User <|-- Student
    User <|-- Administrator
    AccountRepository <|.. FileAccountRepository
    AccountService --> AccountRepository
    AccountService --> AccountState
    AccountState --> AccessRequest
    AccountState --> Student
    AccountState --> TimeAllowance
    AccountState --> AllowanceChange
    JobService --> AccountService
    JobService --> Scheduler
    Scheduler o--> Job
    Scheduler ..> GPU
    Scheduler ..> Prediction
    AccountFrame --> AccountService
    AccountFrame --> JobService
    Main --> AccountFrame
```

- `User`, `Student`, and `Administrator` demonstrate inheritance and
  polymorphism; `User` is the shared abstract account type.
- `AccountService` applies account validation, authentication, permissions,
  approvals, allowance changes, and job-minute reservation/settlement.
- `AccountRepository` separates account storage from its file implementation.
- `Scheduler` owns the simulation clock, four-GPU dispatch, FCFS queue,
  execution history, predictions, and utilization calculations.
- `JobService` coordinates authenticated job actions with allowances and
  scheduler persistence.
- `AccountFrame` is the Swing interface; domain operations remain in the
  account and scheduling services.

## Assumptions

- A job’s selected size maps to 5, 15, 30, or 60 simulated GPU minutes.
- Jobs submitted in the same simulated minute are ordered by generated job ID.
- The first free GPU is selected by increasing ID; predictions use copied
  availability times and do not change live assignments.
- Running cancellations charge elapsed simulated runtime; queued cancellations
  charge zero. A completed job is charged once for its full duration.
- Account and scheduler snapshots are stored in separate files under `data/`.
  Keep both files together when backing up or moving a simulation.

Screenshots, individual GitHub contribution evidence, and the course report
must be added by the project group during its own demonstration and submission.
