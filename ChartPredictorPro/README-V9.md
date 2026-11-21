# Chart Predictor PRO V9 — Three historical replays and manual image-axis levels

V9 is a native offline Android prototype for uploaded candlestick screenshots and historical OHLCV CSV files.

## Screenshot mode
1. Upload a chart screenshot and crop closely around the candle price plot.
2. Choose a future horizon (1, 3, 5, 10, or 20 candles), and match the displayed timeframe.
3. Optionally press **Calibrate Price Axis (2 known prices)**. Drag two horizontal lines to known axis levels and enter their numeric values. Labels are **approximate and depend on correct axis alignment**. No OCR or live market lookup is used.
4. Press **Analyze Chart**, then choose **Lower / Middle / Upper historical replay** in the scenario selector. The app finds similar older image-candle sequences and displays their **distinct following candles**, retaining individual body, gap, and wick variations.
5. The projected blue area covers the three illustrated paths; it is **not a probability interval**. No model accuracy or buy/sell confidence is claimed from a screenshot.

## Price and support/resistance levels
- With a valid CSV: real OHLC price scale from imported data; support/resistance are simple 20-candle high/low levels, not validated order blocks.
- With two manually positioned screenshot anchors: estimated image-derived price levels, which are only as good as the chart extraction and your calibration.
- With neither: relative image-pixel levels **not market prices**.

## Data-based ensemble
Import at least ~200 chronological OHLCV candles (oldest first) for historical models, indicator calculations, and separate walk-forward error estimates. Only synthetic unit tests are included in the repository; performance on real NIFTY, BANK NIFTY or other market data is unverified.

The app does not use a pretrained vision model or a universally best trading model. It should never be treated as a guaranteed profitable trade signal.
