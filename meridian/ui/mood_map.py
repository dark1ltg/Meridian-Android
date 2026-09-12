from __future__ import annotations

from math import hypot

from PySide6.QtCore import QEvent, QPoint, QPointF, QRectF, Qt, QTimer, Signal
from PySide6.QtGui import (
    QBrush,
    QColor,
    QFont,
    QImage,
    QMouseEvent,
    QNativeGestureEvent,
    QPainter,
    QPainterPath,
    QPalette,
    QPen,
    QPixmap,
    QRadialGradient,
)
from PySide6.QtWidgets import (
    QGestureEvent,
    QGraphicsEllipseItem,
    QGraphicsItem,
    QGraphicsPixmapItem,
    QGraphicsScene,
    QGraphicsSimpleTextItem,
    QGraphicsView,
    QPinchGesture,
)

from meridian.context import LENS_RADIUS_DEFAULT, LENS_RADIUS_MAX, LENS_RADIUS_MIN
from meridian.queue_engine import Quadrant, RankedTrack
from meridian.ui.fonts import sans
from meridian.features import CONFIDENCE_HIGH, CONFIDENCE_LOW
from meridian.ui.palette import (
    LOW_CONFIDENCE_OPACITY,
    LOW_TRUST_ALPHA,
    LOW_TRUST_QCOLOR,
    MID_CONFIDENCE_ALPHA,
    MID_CONFIDENCE_OPACITY,
    PLAYLIST_QCOLOR,
    STAR_RADIUS,
    STAR_Z,
)

# View magnification on top of fit-to-widget (1 = whole night sky).
VIEW_ZOOM_MIN = 1.0
VIEW_ZOOM_MAX = 14.0
VIEW_WHEEL_FACTOR = 1.06
VIEW_NATIVE_OUTLIER = 1.25
# Below this zoom, stars are one baked texture (smooth pinch). Above: live items in view.
LIVE_STARS_ZOOM = 2.4
LOD_LABEL_START = 2.6
LOD_GLOW_START = 2.4
LOD_CHROME_FADE_START = 1.4
LOD_CHROME_FADE_END = 4.0
CLUSTER_PULL = 0.42
CLUSTER_GRID = 48.0
LOD_DEFER_MS = 48
LABEL_CAP = 48
LIVE_STAR_CAP = 220          # max interactive stars while zoomed in
FIELD_SCALE = 2              # bake retina-ish starfield
SCENE_W = 800
SCENE_H = 600
HIT_RADIUS_SKY = 14.0
# Tighter than hover/snap — avoids turning a nearby pan into an accidental pin.
HIT_RADIUS_SKY_GRAB = 7.0
SKY_DRAG_SLOP = 8.0
