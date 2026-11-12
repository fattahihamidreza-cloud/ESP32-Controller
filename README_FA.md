# پروژه ESP32 Controller

نسخه اولیه اپ اندروید برای ESP32-WROOM-32 با Kotlin و Jetpack Compose.

## امکانات فعلی
- اتصال Wi-Fi از طریق TCP Socket (پیش‌فرض IP برابر 192.168.4.1 و پورت 8080)
- اتصال Bluetooth Classic از نوع RFCOMM/SPP به دستگاه Pair شده
- ارسال متن فرمان با پایان خط `\n`
- دریافت پیام‌های متنی خط‌به‌خط و نمایش در گزارش

## محدودیت مهم
این اپ هنوز firmware برد را تولید یا تغییر نمی‌دهد. کد ESP32 باید سرور TCP روی پورت انتخابی و سرویس Bluetooth SPP با UUID استاندارد را اجرا کند. این نسخه از BLE پشتیبانی نمی‌کند. اگر منظورت از Bluetooth، BLE است، باید مشخصات Service UUID و Characteristic UUID برد را داشته باشیم و بخش BLE اضافه شود.

## ساخت در GitHub
1. یک repository جدید در GitHub بساز و نام آن را `ESP32-Controller` بگذار.
2. فایل‌های این پروژه را در شاخه `main` بارگذاری کن.
3. برای ساخت APK، تب Actions را باز کن و workflow با نام `Build Android APK` را اجرا کن.
4. بعد از پایان موفق، در صفحه اجرای workflow بخش Artifacts فایل `ESP32-Controller-APK` را دانلود کن.
5. فایل ZIP دانلودشده را باز کن؛ `app-debug.apk` داخل آن است.

## نکته Gradle Wrapper
Workflow ابتدا Gradle 8.9 را روی سرور GitHub نصب می‌کند، سپس فایل‌های Gradle Wrapper را همان‌جا می‌سازد و APK را کامپایل می‌کند؛ بنابراین نیازی به Android Studio یا Gradle نصب‌شده روی کامپیوتر شما نیست.

## قرارداد نمونه فرمان
- ارسال از اپ: `LED_ON\n`
- پاسخ نمونه firmware: `OK LED=1\n`
- ارسال از اپ: `LED_OFF\n`
- پاسخ نمونه firmware: `OK LED=0\n`

فرمان‌ها در این نسخه صرفاً متن هستند؛ معنای آن‌ها را باید در firmware تعریف کرد.
