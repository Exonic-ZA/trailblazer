/*
 * Copyright 2025 Exonic (info@exonic.co.za)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import org.traccar.api.security.PermissionsService;
import org.traccar.model.Device;
import org.traccar.model.Image;
import org.traccar.model.User;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

/**
 * Single definition of who may see an Image, shared by {@link ImageResource} and
 * {@code UploadsFilter} so the JSON metadata and the stored bytes can never disagree.
 */
public final class ImageAccess {

    private ImageAccess() {
    }

    public static boolean allowed(
            Storage storage, PermissionsService permissionsService, long userId, Image image)
            throws StorageException {

        if (!permissionsService.notAdmin(userId)) {
            return true;
        }

        boolean linked = !storage.getObjects(Image.class, new Request(
                new Columns.Include("id"),
                new Condition.And(
                        new Condition.Equals("id", image.getId()),
                        new Condition.Permission(User.class, userId, Image.class)))).isEmpty();
        if (linked) {
            return true;
        }

        if (image.getDeviceId() > 0) {
            return !storage.getObjects(Device.class, new Request(
                    new Columns.Include("id"),
                    new Condition.And(
                            new Condition.Equals("id", image.getDeviceId()),
                            new Condition.Permission(User.class, userId, Device.class)))).isEmpty();
        }

        return false;
    }

}
