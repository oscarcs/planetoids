package dev.planetoids.paper;

import dev.planetoids.core.Planet;

/** Computes leaf support from geometry without consulting any neighbouring chunks. */
final class LeafSupport {
    private LeafSupport() {}

    static int distance(Planet planet, int innerRadius, long x, int y, long z) {
        long dx = x - planet.x(), dy = y - planet.y(), dz = z - planet.z();
        int best = 7;
        for (int a = -6; a <= 6; a++) {
            for (int b = -6; b <= 6; b++) {
                int horizontal = Math.abs(a) + Math.abs(b);
                if (horizontal >= best) {
                    continue;
                }
                long remaining =
                        (long) innerRadius * innerRadius
                                - (dx + a) * (dx + a)
                                - (dz + b) * (dz + b);
                if (remaining < 0) {
                    continue;
                }
                int verticalReach = (int) Math.sqrt(remaining);
                long vertical = Math.max(0, Math.abs(dy) - verticalReach);
                best = (int) Math.min(best, horizontal + vertical);
            }
        }
        return Math.max(1, best);
    }
}
