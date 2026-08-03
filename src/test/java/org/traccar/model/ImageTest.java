package org.traccar.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImageTest {

    @Test
    public void testLatitudeAccepted() {
        Image image = new Image();
        image.setLatitude(-33.9249);
        assertEquals(-33.9249, image.getLatitude());
        image.setLatitude(-90);
        image.setLatitude(90);
    }

    @Test
    public void testLatitudeRejected() {
        Image image = new Image();
        assertThrows(IllegalArgumentException.class, () -> image.setLatitude(-90.1));
        assertThrows(IllegalArgumentException.class, () -> image.setLatitude(90.1));
    }

    @Test
    public void testLongitudeAccepted() {
        Image image = new Image();
        image.setLongitude(18.4241);
        assertEquals(18.4241, image.getLongitude());
        image.setLongitude(-180);
        image.setLongitude(180);
    }

    @Test
    public void testLongitudeRejected() {
        Image image = new Image();
        assertThrows(IllegalArgumentException.class, () -> image.setLongitude(-180.1));
        assertThrows(IllegalArgumentException.class, () -> image.setLongitude(180.1));
    }

    @Test
    public void testStorageName() {
        assertEquals("tc_images", Image.class.getAnnotation(
                org.traccar.storage.StorageName.class).value());
    }

    @Test
    public void testPermissionTableName() {
        assertEquals("tc_user_image", Permission.getStorageName(User.class, Image.class));
    }

}
