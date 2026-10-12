<div dir="rtl">

# DECISIONS

> מלאו את הקובץ הזה. הוא חלק מההערכה — קצר וברור עדיף על ארוך.

## 1. החלטות ארכיטקטוניות
- ...

## 2. הבאג ביתרת החופשה
  התחלתי מהתיאור במשימה: עובד מצליח להגיש בקשה שחורגת מהמכסה. חיפשתי בקוד את הפונקציה שיוצרת בקשה, `create` ב-`LeaveRequestsController`, ועברתי על הבדיקה שהיא עושה לפני השמירה.
  ראיתי שהקוד מחשב את `used`, כמה ימי חופשה כבר אושרו לעובד, אבל לא משתמש בו. התנאי בדק רק `days > annualQuota`, כלומר אם הבקשה לבדה גדולה מכל המכסה. כך עובדת עם מכסה של 20 ו-18 ימים מאושרים יכלה להגיש עוד 3 ימים.
  התיקון: שיניתי את התנאי ל-`used + days > annualQuota`. 

  - **הטסט שמוכיח את התיקון:**
  הוספתי ל-`LeaveRequestsTests` (Testcontainers מול Postgres אמיתי) שני טסטים:
  - `create_ExceedingQuota_ReturnsBadRequest`: 18 מאושרים ועוד 3, מחזיר 400 ולא נשמרת בקשה.
  - `create_ExactlyReachingQuota_Succeeds`: 18 ועוד 2, בדיוק 20, ועובר. זה מקרה הגבול.
  כל טסט יוצר עובד משלו, כדי לא להיות תלוי ב-DataSeeder.

## 3. אישור בקשה (approve) ו‑concurrency
- **איך טיפלתי במצבים לא חוקיים (כבר אושר / לא קיים):**
  העברתי את הלוגיקה של האישור ל-`LeaveRequestService`. אם הבקשה לא קיימת מוחזר 404. אם היא כבר לא PENDING (אושרה או נדחתה) מוחזר 409. גם כשאין מספיק ימים בזמן האישור החזרתי 409, כי הבקשה עצמה תקינה, פשוט היא מתנגשת עם מה שכבר אושר. את היתרה בודקים רק לחופשה (VACATION).

- **מה לגבי אישור של שתי בקשות במקביל:**
  הבעיה שראיתי: אם לעובדת יש 18 ימים מאושרים ושתי בקשות של 2 ימים, ומאשרים את שתיהן באותו רגע, שתיהן יראו שנוצלו רק 18 ויאושרו. בסוף יהיו לה 22 ימים מתוך 20.
  פתרתי את זה עם נעילה על השורה של העובד (`PESSIMISTIC_WRITE`, כלומר `SELECT ... FOR UPDATE`) בתוך טרנזקציה. ככה אישורים של אותו עובד מחכים אחד לשני, ועובדים שונים לא מפריעים זה לזה.
  הקפדתי על הסדר: קודם אני לוקחת רק את ה-id של העובד, אחר כך נועלת אותו, ורק אז טוענת את הבקשה ובודקת סטטוס ויתרה. ככה הבדיקה תמיד על נתונים עדכניים. זה מטפל גם במקרה שבו לוחצים פעמיים על אישור לאותה בקשה: הלחיצה השנייה מחכה, רואה שהבקשה כבר אושרה ומקבלת 409.
  ביצירת בקשה לא שמתי נעילה, כי שם נוצרת רק בקשה ממתינה, והבדיקה הקובעת היא באישור.
  בדקתי ידנית ב-Swagger את כל המצבים: אישור תקין (200), אישור שחורג מהיתרה (409), אישור כפול של אותה בקשה (409) ובקשה שלא קיימת (404).


## 4. על מה ויתרתי בגלל הזמן
- ... ומה הייתי עושה עם עוד יום:

## 5. שימוש ב‑AI
### איפה AI עזר (כולל prompts)
1. prompt: "..." → מה קיבלתי ומה עשיתי איתו:
2. ...

Prompt:
"Add the regression test requested by the TODO in LeaveRequestsTests.
Follow the existing Arrange/Act/Assert style of create_WithinQuota_Succeeds.

Scenario: employee with annualQuota 20 and an APPROVED VACATION request of 18 days
(save it via LeaveRequestRepository). Then submit a new 3-day VACATION request
through controller.create().

Assert:
- HTTP 400
- leaveRequests.count() is unchanged (compare before/after, don't assume an empty table —
  DataSeeder and other tests share the same database)

Also add a boundary test: 18 approved + 2-day request (exactly 20) must succeed.

Create the employee inside each test (don't rely on seeded data).
Add any missing imports. Do not change production code."
**מה קיבלתי ומה עשיתי עם זה:**
את הבאג עצמו איתרתי ותיקנתי בעצמי: הוספתי את `used` לתנאי שבודק את יתרת החופשה ב-Controller. ב-AI נעזרתי רק לכתיבת הטסטים. עברתי על הקוד שנוצר, וידאתי שהוא בודק גם חריגה מהמכסה וגם את מקרה הגבול שבו מגיעים בדיוק למכסה, והרצתי `mvn test`. כל הטסטים עברו.

### איפה דחיתי/תיקנתי הצעה של AI
- מה AI הציע, למה זה היה שגוי, ומה עשיתי במקום:

### אבטחה
- אם מצאתם בעיית אבטחה: מה מצאתם, איפה (קובץ + שורה), למה זו בעיה, ואיך תיקנתם:

## 6. הוראות הרצה
- (אם שיניתם משהו מהוראות ה‑README המקורי)
- עם Docker Desktop חדש, Testcontainers 1.20.2 נכשל ב-"Could not find a valid Docker environment". פתרון: `echo "api.version=1.44" > ~/.docker-java.properties`

</div>
