@echo off
setlocal

set "_CLI_DB_USER=%EDUEXAM_DB_USER%"
set "_CLI_DB_PASSWORD=%EDUEXAM_DB_PASSWORD%"
set "_CLI_DB_HOST=%EDUEXAM_DB_HOST%"
set "_CLI_DB_PORT=%EDUEXAM_DB_PORT%"
set "_CLI_DB_NAME=%EDUEXAM_DB_NAME%"
set "_CLI_SEED_DEMO=%EDUEXAM_SEED_DEMO%"
set "_CLI_DB_USERNAME_ALT=%DB_USERNAME%"
set "_CLI_DB_PASSWORD_ALT=%DB_PASSWORD%"
set "_CLI_DB_HOST_ALT=%DB_HOST%"
set "_CLI_DB_PORT_ALT=%DB_PORT%"
set "_CLI_DB_NAME_ALT=%DB_NAME%"
if exist "%~dp0.eduexam.local.bat" call "%~dp0.eduexam.local.bat"
if defined _CLI_DB_USER set "EDUEXAM_DB_USER=%_CLI_DB_USER%"
if defined _CLI_DB_PASSWORD set "EDUEXAM_DB_PASSWORD=%_CLI_DB_PASSWORD%"
if defined _CLI_DB_HOST set "EDUEXAM_DB_HOST=%_CLI_DB_HOST%"
if defined _CLI_DB_PORT set "EDUEXAM_DB_PORT=%_CLI_DB_PORT%"
if defined _CLI_DB_NAME set "EDUEXAM_DB_NAME=%_CLI_DB_NAME%"
if defined _CLI_SEED_DEMO set "EDUEXAM_SEED_DEMO=%_CLI_SEED_DEMO%"
if not defined _CLI_DB_USER if defined _CLI_DB_USERNAME_ALT set "EDUEXAM_DB_USER=%_CLI_DB_USERNAME_ALT%"
if not defined _CLI_DB_PASSWORD if defined _CLI_DB_PASSWORD_ALT set "EDUEXAM_DB_PASSWORD=%_CLI_DB_PASSWORD_ALT%"
if not defined _CLI_DB_HOST if defined _CLI_DB_HOST_ALT set "EDUEXAM_DB_HOST=%_CLI_DB_HOST_ALT%"
if not defined _CLI_DB_PORT if defined _CLI_DB_PORT_ALT set "EDUEXAM_DB_PORT=%_CLI_DB_PORT_ALT%"
if not defined _CLI_DB_NAME if defined _CLI_DB_NAME_ALT set "EDUEXAM_DB_NAME=%_CLI_DB_NAME_ALT%"

if not defined EDUEXAM_DB_USER if defined DB_USERNAME set "EDUEXAM_DB_USER=%DB_USERNAME%"
if not defined EDUEXAM_DB_PASSWORD if defined DB_PASSWORD set "EDUEXAM_DB_PASSWORD=%DB_PASSWORD%"
if not defined EDUEXAM_DB_HOST if defined DB_HOST set "EDUEXAM_DB_HOST=%DB_HOST%"
if not defined EDUEXAM_DB_PORT if defined DB_PORT set "EDUEXAM_DB_PORT=%DB_PORT%"
if not defined EDUEXAM_DB_NAME if defined DB_NAME set "EDUEXAM_DB_NAME=%DB_NAME%"

set "PROJECT_DIR=%~dp0"
set "SCHEMA_SQL=%PROJECT_DIR%sql\schema.sql"
set "DATA_SQL=%PROJECT_DIR%sql\data.sql"

if not exist "%SCHEMA_SQL%" (
    echo [ERROR] Missing SQL file: "%SCHEMA_SQL%"
    exit /b 1
)
if not exist "%DATA_SQL%" (
    echo [ERROR] Missing SQL file: "%DATA_SQL%"
    exit /b 1
)

set "MYSQL_EXE=%EDUEXAM_MYSQL_EXE%"
if not defined MYSQL_EXE for %%I in (mysql.exe) do set "MYSQL_EXE=%%~$PATH:I"
if not defined MYSQL_EXE if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL_EXE=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not defined MYSQL_EXE if exist "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" set "MYSQL_EXE=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"

if not defined MYSQL_EXE (
    echo [ERROR] mysql.exe was not found.
    echo Install MySQL or set EDUEXAM_MYSQL_EXE to its full path.
    exit /b 1
)

if not defined EDUEXAM_DB_USER set "EDUEXAM_DB_USER=root"
if not defined EDUEXAM_DB_HOST set "EDUEXAM_DB_HOST=localhost"
if not defined EDUEXAM_DB_PORT set "EDUEXAM_DB_PORT=3306"
if not defined EDUEXAM_DB_NAME set "EDUEXAM_DB_NAME=eduexam"
if not defined EDUEXAM_DB_PASSWORD (
    echo [ERROR] EDUEXAM_DB_PASSWORD is not configured.
    echo Set it in the environment or copy eduexam.local.example.bat to .eduexam.local.bat.
    exit /b 1
)
set "MYSQL_PWD=%EDUEXAM_DB_PASSWORD%"
set "MYSQL_COMMON=--host=%EDUEXAM_DB_HOST% --port=%EDUEXAM_DB_PORT% --user=%EDUEXAM_DB_USER%"

if /I not "%EDUEXAM_DB_NAME%"=="eduexam" (
    echo [ERROR] EDUEXAM_DB_NAME must be eduexam for the bundled SQL bootstrap.
    echo Initialize a custom database separately, or use the default eduexam database.
    exit /b 1
)

echo [Database] Checking schema from "%PROJECT_DIR%sql"...
"%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 < "%SCHEMA_SQL%"
if errorlevel 1 (
    echo [ERROR] Unable to initialize the database.
    echo Check that MySQL is running and the password matches .eduexam.local.bat or your environment variable.
    exit /b 1
)

set "COUNT_FILE=%TEMP%\eduexam_user_count_%RANDOM%_%RANDOM%.txt"
"%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 -N -B -e "SELECT COUNT(*) FROM eduexam.users;" > "%COUNT_FILE%"
if errorlevel 1 (
    if exist "%COUNT_FILE%" del /q "%COUNT_FILE%"
    echo [ERROR] Unable to inspect the eduexam.users table.
    exit /b 1
)

set "USER_COUNT=0"
set /p USER_COUNT=<"%COUNT_FILE%"
del /q "%COUNT_FILE%"

if "%USER_COUNT%"=="0" (
    if /I "%EDUEXAM_SEED_DEMO%"=="true" (
        echo [Database] Empty database detected. Importing demo data because EDUEXAM_SEED_DEMO=true...
        "%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 < "%DATA_SQL%"
        if errorlevel 1 (
            echo [ERROR] Demo data import failed.
            exit /b 1
        )
        echo [Database] Demo data imported successfully.
    ) else (
        echo [Database] Empty database detected. Demo seed skipped.
        echo [Database] Set EDUEXAM_SEED_DEMO=true only for a local demo database.
    )
) else (
    echo [Database] Ready. Found %USER_COUNT% users; seed import skipped.
)

echo [Database] Normalizing legacy exam record statuses...
"%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 -e "UPDATE eduexam.exam_records SET status='graded' WHERE status='completed'; UPDATE eduexam.exam_records SET status='in_progress' WHERE status='started';"
if errorlevel 1 (
    echo [ERROR] Unable to normalize legacy exam record statuses.
    exit /b 1
)

echo [Database] Enforcing one answer per question and exam record...
"%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 -e "USE eduexam; DELETE a FROM answers a INNER JOIN answers a2 ON a2.record_id=a.record_id AND a2.question_id=a.question_id AND a2.id<a.id; SET @has_answer_unique := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='eduexam' AND table_name='answers' AND index_name='uq_answer_record_question'); SET @add_answer_unique := IF(@has_answer_unique=0, 'ALTER TABLE answers ADD UNIQUE KEY uq_answer_record_question (record_id, question_id)', 'SET @answer_index_noop=1'); PREPARE answer_index_stmt FROM @add_answer_unique; EXECUTE answer_index_stmt; DEALLOCATE PREPARE answer_index_stmt;"
if errorlevel 1 (
    echo [ERROR] Unable to enforce answer uniqueness.
    exit /b 1
)

exit /b 0
