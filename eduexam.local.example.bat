@echo off
rem Copy this file to .eduexam.local.bat and replace the values.
rem .eduexam.local.bat is ignored by Git and must never be committed.
set "EDUEXAM_DB_USER=root"
set "EDUEXAM_DB_PASSWORD=replace-with-your-mysql-password"
set "EDUEXAM_DB_HOST=localhost"
set "EDUEXAM_DB_PORT=3306"
set "EDUEXAM_DB_NAME=eduexam"
rem Leave this short until you replace it with a newly generated secret.
set "APP_JWT_SECRET=CHANGE_ME"
rem Set this to true only for a local demo database. Never enable it in production.
set "EDUEXAM_SEED_DEMO=false"
