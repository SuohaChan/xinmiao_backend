@echo off
chcp 65001 >nul
set MYSQL_PWD=123456
set MYSQL_USER=root
set MYSQL_HOST=localhost
set MYSQL_PORT=3306

echo 正在初始化数据库...
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < college.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < class.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < counselor.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < student.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < check_in.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < gift.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < information.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < item.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < notice.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < studentCourse.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < task.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < appeal.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < credit_flow.sql
mysql -u %MYSQL_USER% -h %MYSQL_HOST% -P %MYSQL_PORT% < tb_student_username_unique.sql
echo 数据库初始化完成！
pause
