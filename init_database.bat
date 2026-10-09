@echo off
setlocal

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
if not defined EDUEXAM_DB_PASSWORD set "EDUEXAM_DB_PASSWORD=123456"
set "MYSQL_PWD=%EDUEXAM_DB_PASSWORD%"

echo [Database] Checking schema from "%PROJECT_DIR%sql"...
"%MYSQL_EXE%" --user="%EDUEXAM_DB_USER%" --default-character-set=utf8mb4 < "%SCHEMA_SQL%"
if errorlevel 1 (
    echo [ERROR] Unable to initialize the database.
    echo Check that MySQL is running and the password matches backend\src\main\resources\application.yml.
    exit /b 1
)

set "COUNT_FILE=%TEMP%\eduexam_user_count_%RANDOM%_%RANDOM%.txt"
"%MYSQL_EXE%" --user="%EDUEXAM_DB_USER%" --default-character-set=utf8mb4 -N -B -e "SELECT COUNT(*) FROM eduexam.users;" > "%COUNT_FILE%"
if errorlevel 1 (
    if exist "%COUNT_FILE%" del /q "%COUNT_FILE%"
    echo [ERROR] Unable to inspect the eduexam.users table.
    exit /b 1
)

set "USER_COUNT=0"
set /p USER_COUNT=<"%COUNT_FILE%"
del /q "%COUNT_FILE%"

if "%USER_COUNT%"=="0" (
    echo [Database] Empty database detected. Importing demo data...
    "%MYSQL_EXE%" --user="%EDUEXAM_DB_USER%" --default-character-set=utf8mb4 < "%DATA_SQL%"
    if errorlevel 1 (
        echo [ERROR] Demo data import failed.
        exit /b 1
    )
    echo [Database] Demo data imported successfully.
) else (
    echo [Database] Ready. Found %USER_COUNT% users; seed import skipped.
)

exit /b 0
