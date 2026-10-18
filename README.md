# NSE Bhavcopy Trend Scanner

Developed By: Gaurav Kharva

Android APK to import NSE PR ZIP files manually. Looks for `pdDDMMYYYY.csv` in the ZIP; stores the most recent 30 distinct trading dates locally in SQLite.

### Features
- **Volume +50%**: latest traded quantity greater than 150% of the previous uploaded trading day's quantity, same symbol and series.
- **Price +5%**: latest closing price more than 5% above NSE's previous-close value.
- **Combined**: both tests above.
- **Consistent uptrend**: closing price AND daily traded volume rise for at least 3 successive trading dates.
- **Consistent downtrend**: closing price declines while daily traded volume rises for at least 3 successive trading dates.
- Search symbols, filter signals, export a CSV report.
- No data subscription and no automatic download. Upload at least 2 daily files for volume comparison and preferably 5 for trend context.

### Build
Go to **Actions → Build NSE Bhavcopy APK** and select the latest successful run. Under **Artifacts**, download `NSE-Bhavcopy-Scanner-APK`; extract and install `app-debug.apk`.

### Important
The imported files must be compatible with NSE `PR*.zip` / `pdDDMMYYYY.csv` format and include MKT, SYMBOL, SERIES, SECURITY, PREV_CL_PR, CLOSE_PRICE, NET_TRDQTY columns. Input may contain additional columns. Only market code N is included.

This application is an analysis tool and does not predict returns or place trades.
