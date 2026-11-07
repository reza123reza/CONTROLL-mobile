# 📱 CONTROLL-mobile

**English** · [فارسی](#فارسی)

An Android app ("کنترل لپ‌تاپ" – *Laptop Control*) that connects your phone to a **Remote-PC** server running on your laptop. The app is a thin WebView client: it finds the server, signs in with a PIN and shows the control page. It can also share your phone screen to the laptop.

> The Remote-PC server itself is not part of this repository.

## Features

- **Automatic server discovery** — tries the last used address, then a UDP broadcast on port 8766, then a scan of the local network (port 8765), and finally an optional remote URL
- **PIN login** — signs in through the server and opens the control page inside the app
- **Phone screen sharing** — streams your phone screen to the laptop as JPEG frames (Android screen capture running as a foreground service)
- **Menu** — toggle sharing, reconnect, open the Bale bot link and edit settings (PIN, remote URL, Bale link)

## Requirements

Android 8.0 (API 26) or newer. Built with Kotlin.

## Build

Open the project in Android Studio, or run the **Build APK** workflow under **Actions** on GitHub — the APK is uploaded as the `RemotePC-apk` artifact.

---

<div dir="rtl">

## فارسی

# 📱 کنترل موبایل

یک برنامهٔ اندروید («کنترل لپ‌تاپ») که گوشی را به سرور **Remote-PC** روی لپ‌تاپ وصل می‌کند. برنامه یک کلاینت سبک مبتنی بر WebView است: سرور را پیدا می‌کند، با رمز (PIN) وارد می‌شود و صفحهٔ کنترل را نشان می‌دهد. همچنین می‌تواند صفحهٔ گوشی را برای لپ‌تاپ به اشتراک بگذارد.

> خودِ سرور Remote-PC در این مخزن نیست.

## امکانات

- **پیدا کردن خودکار سرور** — اول آخرین آدرس، بعد پخش UDP روی پورت ۸۷۶۶، بعد اسکن شبکهٔ محلی (پورت ۸۷۶۵) و در آخر آدرس راه‌دور اختیاری
- **ورود با PIN** — ورود از طریق سرور و باز شدن صفحهٔ کنترل داخل برنامه
- **اشتراک صفحهٔ گوشی** — ارسال صفحهٔ گوشی به لپ‌تاپ به‌صورت تصویرهای JPEG (ضبط صفحهٔ اندروید در یک سرویس پیش‌زمینه)
- **منو** — روشن/خاموش کردن اشتراک، اتصال دوباره، لینک ربات بله و تنظیمات (PIN، آدرس راه‌دور، لینک بله)

## نیازمندی‌ها

اندروید ۸٫۰ (API 26) یا بالاتر. نوشته‌شده با Kotlin.

## ساخت

پروژه را در Android Studio باز کن، یا Workflow به نام **Build APK** را در بخش **Actions** گیت‌هاب اجرا کن — فایل APK به‌عنوان `RemotePC-apk` بارگذاری می‌شود.

</div>
