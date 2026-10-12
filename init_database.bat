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

echo [Database] Ensuring database exists...
"%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS eduexam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if errorlevel 1 (
    echo [ERROR] Unable to create or inspect the database.
    echo Check that MySQL is running and the password matches .eduexam.local.bat or your environment variable.
    exit /b 1
)

set "TABLE_FILE=%TEMP%\eduexam_table_count_%RANDOM%_%RANDOM%.txt"
"%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='eduexam' AND table_name IN ('users','categories','tags','questions','question_tags','courses','course_enrollments','exams','exam_questions','exam_records','answers','wrong_book','notifications');" > "%TABLE_FILE%"
if errorlevel 1 (
    if exist "%TABLE_FILE%" del /q "%TABLE_FILE%"
    echo [ERROR] Unable to inspect the eduexam database.
    exit /b 1
)
set "REQUIRED_TABLE_COUNT=0"
set /p REQUIRED_TABLE_COUNT=<"%TABLE_FILE%"
del /q "%TABLE_FILE%"

if not "%REQUIRED_TABLE_COUNT%"=="13" (
    echo [Database] New or incomplete database detected. Applying the idempotent initial schema from "%PROJECT_DIR%sql"...
    "%MYSQL_EXE%" %MYSQL_COMMON% --default-character-set=utf8mb4 < "%SCHEMA_SQL%"
    if errorlevel 1 (
        echo [ERROR] Unable to initialize the database.
        exit /b 1
    )
) else (
    echo [Database] Existing schema detected. Versioned upgrades will be applied by Flyway.
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

echo [Database] Bootstrap complete. Flyway will validate and apply pending migrations when the backend starts.

exit /b 0
