import cv2
import numpy as np


def _decode_image(image_bytes):
    data = np.frombuffer(image_bytes, dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)

    if img is None:
        raise Exception("OpenCV decode failed")

    return img


def _encode_png(image):
    success, buffer = cv2.imencode(".png", image)

    if not success:
        raise Exception("OpenCV encode failed")

    return buffer.tobytes()


def order_points(pts):
    rect = np.zeros((4, 2), dtype="float32")

    s = pts.sum(axis=1)
    rect[0] = pts[np.argmin(s)]  # 左上
    rect[2] = pts[np.argmax(s)]  # 右下

    diff = np.diff(pts, axis=1)
    rect[1] = pts[np.argmin(diff)]  # 右上
    rect[3] = pts[np.argmax(diff)]  # 左下

    return rect


def four_point_transform(image, pts):
    rect = order_points(pts)
    tl, tr, br, bl = rect

    width_a = np.linalg.norm(br - bl)
    width_b = np.linalg.norm(tr - tl)
    max_width = int(max(width_a, width_b))

    height_a = np.linalg.norm(tr - br)
    height_b = np.linalg.norm(tl - bl)
    max_height = int(max(height_a, height_b))

    dst = np.array([
        [0, 0],
        [max_width - 1, 0],
        [max_width - 1, max_height - 1],
        [0, max_height - 1]
    ], dtype="float32")

    m = cv2.getPerspectiveTransform(rect, dst)
    warped = cv2.warpPerspective(image, m, (max_width, max_height))

    return warped


def canny_process(image_bytes):
    img = _decode_image(image_bytes)

    original = img.copy()

    # 縮小處理，加快速度
    ratio = img.shape[0] / 500.0
    resized = cv2.resize(img, (int(img.shape[1] / ratio), 500))

    gray = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (5, 5), 0)

    edged = cv2.Canny(gray, 75, 200)

    contours, _ = cv2.findContours(
        edged.copy(),
        cv2.RETR_LIST,
        cv2.CHAIN_APPROX_SIMPLE
    )

    contours = sorted(contours, key=cv2.contourArea, reverse=True)[:5]

    screen_cnt = None

    for c in contours:
        peri = cv2.arcLength(c, True)
        approx = cv2.approxPolyDP(c, 0.02 * peri, True)

        if len(approx) == 4:
            screen_cnt = approx
            break

    # 如果找不到文件邊框，先回傳 Canny 圖，方便除錯
    if screen_cnt is None:
        return _encode_png(edged)

    pts = screen_cnt.reshape(4, 2) * ratio
    warped = four_point_transform(original, pts)

    # 掃描效果：灰階 + 自適應二值化
    warped_gray = cv2.cvtColor(warped, cv2.COLOR_BGR2GRAY)
    scanned = cv2.adaptiveThreshold(
        warped_gray,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        11,
        2
    )

    return _encode_png(scanned)


def gamma_process(image_bytes, gamma=1.35):
    img = _decode_image(image_bytes)

    inv_gamma = 1.0 / gamma
    table = np.array([
        ((i / 255.0) ** inv_gamma) * 255
        for i in np.arange(0, 256)
    ]).astype("uint8")

    corrected = cv2.LUT(img, table)
    return _encode_png(corrected)


def clahe_process(image_bytes):
    img = _decode_image(image_bytes)

    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l_channel, a_channel, b_channel = cv2.split(lab)

    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    enhanced_l = clahe.apply(l_channel)

    merged = cv2.merge((enhanced_l, a_channel, b_channel))
    enhanced = cv2.cvtColor(merged, cv2.COLOR_LAB2BGR)
    return _encode_png(enhanced)


def diagnose_process(image_bytes):
    img = _decode_image(image_bytes)
    output = img.copy()

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    mean_value = float(np.mean(gray))
    std_value = float(np.std(gray))

    if mean_value < 80:
        diagnosis = "Underexposed"
        color = (80, 180, 255)
    elif mean_value > 185:
        diagnosis = "Overexposed"
        color = (80, 80, 255)
    elif std_value < 35:
        diagnosis = "Low contrast"
        color = (80, 255, 255)
    else:
        diagnosis = "Exposure OK"
        color = (90, 220, 120)

    h, w = output.shape[:2]
    panel_h = max(80, int(h * 0.12))
    cv2.rectangle(output, (0, 0), (w, panel_h), (20, 20, 20), -1)
    cv2.putText(
        output,
        diagnosis,
        (24, int(panel_h * 0.45)),
        cv2.FONT_HERSHEY_SIMPLEX,
        max(0.8, w / 900.0),
        color,
        2,
        cv2.LINE_AA
    )
    cv2.putText(
        output,
        "brightness %.1f | contrast %.1f" % (mean_value, std_value),
        (24, int(panel_h * 0.78)),
        cv2.FONT_HERSHEY_SIMPLEX,
        max(0.55, w / 1300.0),
        (230, 230, 230),
        2,
        cv2.LINE_AA
    )

    return _encode_png(output)


def text_extract_preview(image_bytes):
    img = _decode_image(image_bytes)

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (3, 3), 0)
    binary = cv2.adaptiveThreshold(
        blur,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31,
        15
    )

    return _encode_png(binary)


def text_region_report(image_bytes):
    img = _decode_image(image_bytes)

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    binary = cv2.adaptiveThreshold(
        gray,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY_INV,
        31,
        15
    )

    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (18, 3))
    connected = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, kernel)
    contours, _ = cv2.findContours(connected, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    regions = 0
    for contour in contours:
        x, y, w, h = cv2.boundingRect(contour)
        if w > 24 and h > 8:
            regions += 1

    return "偵測到約 %d 個文字區塊。此頁已完成 OCR 前處理，可接 ML Kit 或 Tesseract 進行正式辨識。" % regions


def id_photo_preview(image_bytes):
    img = _decode_image(image_bytes)
    h, w = img.shape[:2]

    target_w, target_h = 413, 531
    crop_ratio = target_w / target_h
    source_ratio = w / h

    if source_ratio > crop_ratio:
        new_w = int(h * crop_ratio)
        x0 = (w - new_w) // 2
        cropped = img[:, x0:x0 + new_w]
    else:
        new_h = int(w / crop_ratio)
        y0 = max(0, (h - new_h) // 3)
        cropped = img[y0:y0 + new_h, :]

    resized = cv2.resize(cropped, (target_w, target_h))
    canvas = np.full((target_h + 54, target_w, 3), (245, 247, 250), dtype=np.uint8)
    canvas[:target_h, :] = resized
    cv2.putText(
        canvas,
        "ID PHOTO PREVIEW",
        (42, target_h + 34),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.75,
        (70, 80, 90),
        2,
        cv2.LINE_AA
    )
    return _encode_png(canvas)


def formula_preview(image_bytes):
    img = _decode_image(image_bytes)
    output = img.copy()

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    binary = cv2.adaptiveThreshold(
        gray,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY_INV,
        31,
        11
    )
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (28, 5))
    connected = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, kernel)
    contours, _ = cv2.findContours(connected, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    count = 0
    for contour in contours:
        x, y, w, h = cv2.boundingRect(contour)
        if w > 45 and 8 < h < 90:
            count += 1
            cv2.rectangle(output, (x, y), (x + w, y + h), (90, 220, 160), 3)

    cv2.rectangle(output, (0, 0), (output.shape[1], 70), (20, 20, 20), -1)
    cv2.putText(
        output,
        "Formula candidates: %d" % count,
        (20, 46),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.9,
        (90, 220, 160),
        2,
        cv2.LINE_AA
    )
    return _encode_png(output)


def translate_preview(image_bytes):
    img = _decode_image(image_bytes)

    binary_bytes = text_extract_preview(image_bytes)
    binary = cv2.imdecode(np.frombuffer(binary_bytes, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
    preview = cv2.cvtColor(binary, cv2.COLOR_GRAY2BGR)

    h, w = preview.shape[:2]
    cv2.rectangle(preview, (0, 0), (w, max(78, int(h * 0.12))), (28, 31, 36), -1)
    cv2.putText(
        preview,
        "Photo translation preprocessing",
        (20, 48),
        cv2.FONT_HERSHEY_SIMPLEX,
        max(0.65, w / 1100.0),
        (95, 141, 245),
        2,
        cv2.LINE_AA
    )
    return _encode_png(preview)


def watermark_preview(image_bytes):
    img = _decode_image(image_bytes)
    output = img.copy()

    h, w = output.shape[:2]
    overlay_h = max(72, int(h * 0.12))
    overlay = output.copy()
    cv2.rectangle(overlay, (0, h - overlay_h), (w, h), (25, 27, 32), -1)
    output = cv2.addWeighted(overlay, 0.55, output, 0.45, 0)

    cv2.putText(
        output,
        "ScanMate Watermark",
        (24, h - int(overlay_h * 0.42)),
        cv2.FONT_HERSHEY_SIMPLEX,
        max(0.8, w / 1000.0),
        (255, 255, 255),
        2,
        cv2.LINE_AA
    )
    cv2.putText(
        output,
        "Final project demo",
        (24, h - int(overlay_h * 0.16)),
        cv2.FONT_HERSHEY_SIMPLEX,
        max(0.55, w / 1400.0),
        (180, 220, 210),
        2,
        cv2.LINE_AA
    )
    return _encode_png(output)
