package local.bodaddon.spatial;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** The inverse of the matrices used to draw Spatial GUI, in camera-relative world space. */
public final class CameraProjection {
    private final Matrix4f inverseViewProjection = new Matrix4f();
    private final Vector3f origin = new Vector3f();
    private boolean valid;

    public void capture(Matrix4fc projection, Matrix4fc view) {
        valid = false;
        if (projection == null || view == null || !projection.isFinite() || !view.isFinite()) {
            return;
        }

        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        if (Math.abs(view.determinant()) < 1.0e-12f
                || Math.abs(viewProjection.determinant()) < 1.0e-12f) {
            return;
        }

        new Matrix4f(view).invert().transformPosition(0, 0, 0, origin);
        viewProjection.invert(inverseViewProjection);
        valid = origin.isFinite() && inverseViewProjection.isFinite();
    }

    public void clear() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    public Vector3f relativeToOrigin(Vector3f point) {
        return new Vector3f(point).sub(origin);
    }

    public float[] ray(double x, double y, int width, int height) {
        if (!valid || width <= 0 || height <= 0 || !Double.isFinite(x) || !Double.isFinite(y)) {
            return null;
        }

        Vector3f direction = inverseViewProjection.transformProject(
                (float) (2.0 * x / width - 1.0),
                (float) (1.0 - 2.0 * y / height),
                -1.0f, new Vector3f()).sub(origin);
        if (!direction.isFinite() || direction.lengthSquared() < 1.0e-12f) {
            return null;
        }
        direction.normalize();
        return new float[] {direction.x, direction.y, direction.z};
    }
}
