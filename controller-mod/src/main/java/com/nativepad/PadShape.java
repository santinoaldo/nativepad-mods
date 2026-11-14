package com.nativepad;

import java.util.ArrayList;
import java.util.List;

/**
 * The controller overlay's outline: one smooth closed curve (a modern two-grip pad, no branding), plus a
 * triangulation of it so it can be filled as a single solid shape. Plain Java, no Minecraft code, so the
 * geometry can be previewed outside the game. Units: the pad is {@link #W} x {@link #H}.
 */
final class PadShape {

    static final float W = 120, H = 82;

    /** Right half as cubic Bezier segments (start, control 1, control 2, end), mirrored for the left half. */
    private static final float[][] RIGHT_HALF = {
            // top edge from the center out to the shoulder
            {60, 13, 70, 13, 82, 11.5f, 92, 10},
            // rounded shoulder down into the side
            {92, 10, 106, 8.5f, 114, 14, 116.5f, 26},
            // outer side of the grip, bulging out then curving in to the tip
            {116.5f, 26, 119.5f, 40, 121, 60, 115, 73},
            // round bottom of the grip
            {115, 73, 111, 81, 99, 82, 95, 74},
            // inner edge of the grip, up into the arch between the grips
            {95, 74, 91, 66, 87, 57.5f, 79, 54.5f},
            // arch across the bottom middle
            {79, 54.5f, 72, 52, 66, 51.5f, 60, 51.5f},
    };

    /** Outline points (x0, y0, x1, y1, ...), clockwise starting at the top center. */
    static final float[] OUTLINE = build();
    /** Triangles over {@link #OUTLINE}: indexes of points, three per triangle. */
    static final int[] TRIANGLES = triangulate(OUTLINE);

    private PadShape() {
    }


    private static float[] build() {
        List<float[]> right = new ArrayList<float[]>();
        int steps = 10;
        for (float[] s : RIGHT_HALF) {
            for (int i = 0; i < steps; i++) { // the end point is the next segment's start
                float t = i / (float) steps, u = 1 - t;
                float x = u * u * u * s[0] + 3 * u * u * t * s[2] + 3 * u * t * t * s[4] + t * t * t * s[6];
                float y = u * u * u * s[1] + 3 * u * u * t * s[3] + 3 * u * t * t * s[5] + t * t * t * s[7];
                right.add(new float[]{x, y});
            }
        }
        right.add(new float[]{60, 51.5f}); // bottom center
        List<float[]> all = new ArrayList<float[]>(right);
        // Left half: mirror the right half back up, skipping the shared center points.
        for (int i = right.size() - 2; i >= 1; i--) {
            float[] p = right.get(i);
            all.add(new float[]{W - p[0], p[1]});
        }
        float[] out = new float[all.size() * 2];
        for (int i = 0; i < all.size(); i++) {
            out[i * 2] = all.get(i)[0];
            out[i * 2 + 1] = all.get(i)[1];
        }
        return out;
    }

    /** Ear clipping: splits a simple polygon (convex or not) into triangles that exactly cover it. */
    static int[] triangulate(float[] pts) {
        int n = pts.length / 2;
        List<Integer> idx = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) idx.add(i);
        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1];
        }
        boolean ccw = area > 0;
        List<Integer> tris = new ArrayList<Integer>();
        int guard = 0;
        while (idx.size() > 3 && guard++ < 10000) {
            boolean clipped = false;
            for (int k = 0; k < idx.size(); k++) {
                int a = idx.get((k + idx.size() - 1) % idx.size()), b = idx.get(k), c = idx.get((k + 1) % idx.size());
                double cross = (pts[b * 2] - pts[a * 2]) * (pts[c * 2 + 1] - pts[a * 2 + 1])
                        - (pts[b * 2 + 1] - pts[a * 2 + 1]) * (pts[c * 2] - pts[a * 2]);
                if (ccw ? cross <= 0 : cross >= 0) continue; // reflex corner: not an ear
                boolean empty = true;
                for (int m : idx) {
                    if (m == a || m == b || m == c) continue;
                    if (inside(pts, m, a, b, c)) {
                        empty = false;
                        break;
                    }
                }
                if (!empty) continue;
                tris.add(a);
                tris.add(b);
                tris.add(c);
                idx.remove(k);
                clipped = true;
                break;
            }
            if (!clipped) break; // shouldn't happen for a simple outline
        }
        if (idx.size() == 3) {
            tris.add(idx.get(0));
            tris.add(idx.get(1));
            tris.add(idx.get(2));
        }
        int[] out = new int[tris.size()];
        for (int i = 0; i < out.length; i++) out[i] = tris.get(i);
        return out;
    }

    private static boolean inside(float[] p, int m, int a, int b, int c) {
        double px = p[m * 2], py = p[m * 2 + 1];
        double d1 = side(px, py, p[a * 2], p[a * 2 + 1], p[b * 2], p[b * 2 + 1]);
        double d2 = side(px, py, p[b * 2], p[b * 2 + 1], p[c * 2], p[c * 2 + 1]);
        double d3 = side(px, py, p[c * 2], p[c * 2 + 1], p[a * 2], p[a * 2 + 1]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static double side(double px, double py, double ax, double ay, double bx, double by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }
}
