#!/usr/bin/env python3
"""Generate original, synthetic educational candlestick illustrations.
Every chart is a training illustration, not real market data or a forecast.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, random, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "../app/src/main/assets/patterns"))
os.makedirs(OUT, exist_ok=True)

PATTERNS = [
    ("Double Top", [(0,.71),(.18,.31),(.34,.58),(.52,.30),(.75,.63),(1,.84)]),
    ("Double Bottom", [(0,.28),(.19,.74),(.33,.46),(.53,.75),(.76,.39),(1,.18)]),
    ("Head and Shoulders",[(0,.73),(.14,.41),(.25,.63),(.41,.15),(.52,.60),(.68,.40),(.80,.67),(1,.85)]),
    ("Inverse Head and Shoulders",[(0,.31),(.16,.64),(.27,.41),(.43,.87),(.54,.43),(.7,.64),(.82,.33),(1,.15)]),
    ("Ascending Triangle",[(0,.82),(.12,.28),(.22,.69),(.35,.29),(.45,.56),(.58,.29),(.7,.44),(.8,.28),(1,.10)]),
    ("Descending Triangle",[(0,.17),(.13,.74),(.25,.29),(.37,.73),(.5,.46),(.63,.73),(.77,.57),(.85,.74),(1,.91)]),
    ("Symmetrical Triangle",[(0,.20),(.11,.80),(.23,.34),(.36,.67),(.49,.44),(.61,.59),(.73,.48),(.84,.53),(1,.27)]),
    ("Bull Flag",[(0,.83),(.16,.31),(.25,.13),(.36,.29),(.48,.17),(.58,.34),(.69,.24),(.79,.38),(.88,.18),(1,.04)]),
    ("Bear Flag",[(0,.14),(.15,.66),(.25,.85),(.38,.71),(.49,.82),(.6,.65),(.72,.76),(.81,.62),(.9,.81),(1,.96)]),
    ("Rising Wedge",[(0,.83),(.12,.25),(.25,.64),(.37,.27),(.49,.49),(.61,.28),(.73,.40),(.83,.29),(1,.80)]),
    ("Falling Wedge",[(0,.17),(.13,.76),(.25,.34),(.38,.74),(.51,.48),(.64,.73),(.76,.57),(.86,.71),(1,.18)]),
    ("Cup and Handle",[(0,.26),(.16,.52),(.3,.79),(.46,.83),(.62,.58),(.73,.24),(.84,.41),(.91,.31),(1,.10)]),
    ("Rounding Bottom",[(0,.20),(.16,.50),(.31,.70),(.48,.78),(.65,.64),(.82,.40),(1,.13)]),
    ("Bullish Rectangle",[(0,.82),(.15,.30),(.25,.71),(.37,.31),(.47,.72),(.59,.30),(.72,.69),(.82,.30),(1,.10)]),
    ("Bearish Rectangle",[(0,.17),(.16,.73),(.27,.30),(.38,.72),(.50,.30),(.62,.74),(.74,.32),(.85,.73),(1,.92)]),
    ("Ascending Channel",[(0,.79),(.12,.49),(.24,.69),(.37,.34),(.5,.58),(.62,.24),(.75,.44),(.87,.12),(1,.28)]),
    ("Descending Channel",[(0,.19),(.12,.47),(.24,.27),(.37,.60),(.49,.39),(.62,.73),(.74,.53),(.87,.86),(1,.71)]),
    ("Doji",[(0,.70),(.13,.52),(.25,.59),(.36,.36),(.5,.46),(.65,.27),(.8,.38),(.9,.26),(1,.28)]),
    ("Hammer",[(0,.30),(.15,.40),(.3,.55),(.45,.64),(.6,.70),(.75,.79),(.87,.66),(1,.47)]),
    ("Shooting Star",[(0,.80),(.16,.62),(.32,.48),(.47,.40),(.63,.24),(.77,.16),(.89,.30),(1,.49)]),
    ("Bullish Engulfing",[(0,.31),(.14,.40),(.29,.45),(.43,.56),(.57,.64),(.7,.74),(.85,.63),(1,.28)]),
    ("Bearish Engulfing",[(0,.78),(.14,.61),(.29,.55),(.45,.42),(.59,.32),(.73,.24),(.86,.35),(1,.76)]),
    ("Morning Star",[(0,.22),(.15,.35),(.29,.48),(.43,.62),(.57,.74),(.7,.82),(.84,.70),(1,.27)]),
    ("Evening Star",[(0,.78),(.17,.64),(.29,.50),(.43,.32),(.57,.20),(.72,.15),(.84,.30),(1,.72)]),
]
W,H=1440,810
def font(px,bold=False):
    for path in (["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"] if bold else ["/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]):
        try: return ImageFont.truetype(path,px)
        except Exception: pass
    return ImageFont.load_default()

def curve(points,t):
    t=max(0,min(1,t))
    for i in range(1,len(points)):
        if t<=points[i][0]:
            a,b=points[i-1],points[i]
            k=(t-a[0])/(b[0]-a[0])
            k=k*k*(3-2*k)
            return a[1]*(1-k)+b[1]*k
    return points[-1][1]

for ix,(title,path) in enumerate(PATTERNS):
    rng=random.Random(2142+ix*17)
    # Detailed premium gradient illustration background, visible noise texture.
    im=Image.new("RGB",(W,H),(9,19,34))
    pix=im.load()
    for y in range(H):
        yk=y/H
        for x in range(W):
            wave=2.8*math.sin(x/110+y/150)
            noise=rng.choice([-2,-1,0,0,0,1,2])
            pix[x,y]=(max(0,int(9+8*yk+wave+noise)),
                      max(0,int(18+13*yk+wave+noise)),
                      max(0,int(33+18*yk+wave+noise)))
    d=ImageDraw.Draw(im)
    d.rounded_rectangle((45,98,1392,747),radius=28,fill=(13,27,45),outline=(43,69,96),width=2)
    d.text((64,34),"CHART PREDICTOR PRO   /   OFFLINE PATTERN ATLAS",font=font(22,True),fill=(102,211,196))
    d.text((70,121),f"{ix+1:02d}    {title}",font=font(45,True),fill=(231,240,250))
    d.text((72,181),"SYNTHETIC EXAMPLE  ·  EDUCATION ONLY",font=font(18),fill=(146,159,176))
    X0,X1,Y0,Y1=93,1312,243,676
    for i in range(7):
        y=Y0+i*(Y1-Y0)/6
        d.line((X0,y,X1,y),fill=(35,54,76),width=2)
    for i in range(13):
        x=X0+i*(X1-X0)/12
        d.line((x,Y0,x,Y1),fill=(28,48,67),width=2)
    candles=76
    series=[]
    for i in range(candles):
        t=i/(candles-1)
        yFrac=curve(path,t)+rng.gauss(0,.025)
        close=100-yFrac*45
        series.append(close)
    for i in range(candles):
        op=series[i-1] if i else series[0]-rng.uniform(-1.5,1.5)
        cl=series[i]
        wd=(X1-X0)/candles
        x=X0+(i+.55)*wd
        yOp=Y1-(op-49)*((Y1-Y0)/55)
        yCl=Y1-(cl-49)*((Y1-Y0)/55)
        hi=max(op,cl)+rng.uniform(.7,2.4)
        lo=min(op,cl)-rng.uniform(.7,2.4)
        yHi=Y1-(hi-49)*((Y1-Y0)/55)
        yLo=Y1-(lo-49)*((Y1-Y0)/55)
        up=cl>=op
        color=(37,210,153) if up else (245,89,108)
        d.line((x,yHi,x,yLo),fill=color,width=3)
        d.rounded_rectangle((x-5,min(yOp,yCl),x+5,max(min(yOp,yCl)+3,max(yOp,yCl))),radius=1,fill=color)
    # A gold trend overlay based on prior closes, avoiding future leakage in demo.
    pts=[]
    for i in range(14,candles):
        a=sum(series[i-13:i+1])/14
        x=X0+(i+.55)*(X1-X0)/candles
        y=Y1-(a-49)*((Y1-Y0)/55)
        pts.append((x,y))
    d.line(pts,fill=(244,189,92),width=4,joint="curve")
    d.rounded_rectangle((1060,125,1344,200),radius=14,fill=(21,41,60),outline=(48,86,108))
    d.text((1084,138),"TRAINING PATTERN",font=font(17,True),fill=(70,218,183))
    d.text((1084,166),"not a live signal",font=font(18),fill=(202,217,232))
    d.line((76,716,1352,716),fill=(45,72,97),width=2)
    d.text((76,724),"Green: close > open    Red: close < open    Gold: 14-period demo average",font=font(16),fill=(164,180,196))
    d.text((1110,724),"ILLUSTRATION",font=font(15,True),fill=(173,190,206))
    im.save(os.path.join(OUT,f"pattern_{ix+1:02d}.png"),"PNG",optimize=True,compress_level=6)
    if ix in (0,11,23):
        print(f"Generated {ix+1}/{len(PATTERNS)}: {title}")
size=sum(os.path.getsize(os.path.join(OUT,f)) for f in os.listdir(OUT))
print(f"Offline gallery total: {size/1024/1024:.2f} MiB ({len(PATTERNS)} original examples)")
