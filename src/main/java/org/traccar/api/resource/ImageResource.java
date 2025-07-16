package org.traccar.api.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.traccar.api.BaseObjectResource;
import org.traccar.database.MediaManager;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.model.Image;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Path("images")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ImageResource extends BaseObjectResource<Image> {

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int IMAGE_SIZE_LIMIT = 10000000;

    @Inject
    private MediaManager mediaManager;

    public ImageResource() {
        super(Image.class);
    }

    private String imageExtension(String type) {
        return switch (type) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> throw new IllegalArgumentException("Unsupported image type");
        };
    }

    private Collection<Device> getDevicesForGroups(List<Long> groupIds) throws StorageException {
        if (groupIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Device> result = new HashSet<>();

        // Get all devices the user has access to
        Collection<Device> userDevices = storage.getObjects(Device.class, new Request(
                new Columns.All(),
                new Condition.Permission(User.class, getUserId(), Device.class)));

        // Get all groups the user has access to
        Collection<Group> userGroups = storage.getObjects(Group.class, new Request(
                new Columns.All(),
                new Condition.Permission(User.class, getUserId(), Group.class)));

        // Create a set of all requested group IDs and their descendants
        Set<Long> expandedGroupIds = new HashSet<>(groupIds);

        // Expand group hierarchy
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Group group : userGroups) {
                if (group.getGroupId() > 0 && expandedGroupIds.contains(group.getGroupId())) {
                    if (expandedGroupIds.add(group.getId())) {
                        changed = true;
                    }
                }
            }
        }

        // Find devices that belong to any of the expanded groups
        for (Device device : userDevices) {
            if (device.getGroupId() > 0 && expandedGroupIds.contains(device.getGroupId())) {
                result.add(device);
            }
        }

        return result;
    }

    @GET
    public Collection<Image> get(
            @QueryParam("all") boolean all,
            @QueryParam("userId") long userId,
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("id") List<Long> imageIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {

        if (!deviceIds.isEmpty() || !groupIds.isEmpty() || !imageIds.isEmpty() || from != null || to != null) {

            List<Image> result = new LinkedList<>();
            Set<Long> targetDeviceIds = new HashSet<>(deviceIds);

            if (!groupIds.isEmpty()) {
                // Check permissions for all group IDs first
                for (Long groupId : groupIds) {
                    permissionsService.checkPermission(Group.class, getUserId(), groupId);
                }

                // Get devices for the specified groups using the simpler method
                Collection<Device> groupDevices = getDevicesForGroups(groupIds);
                for (Device device : groupDevices) {
                    targetDeviceIds.add(device.getId());
                }
            }

            var baseConditions = new LinkedList<Condition>();
            baseConditions.add(new Condition.Permission(User.class, getUserId(), Image.class));

            if (from != null && to != null) {
                baseConditions.add(new Condition.Between("uploadedAt", "from", from, "to", to));
            } else if (from != null) {
                baseConditions.add(new Condition.Compare("uploadedAt", ">=", "from", from));
            } else if (to != null) {
                baseConditions.add(new Condition.Compare("uploadedAt", "<=", "to", to));
            }

            if (!imageIds.isEmpty()) {
                for (Long imageId : imageIds) {
                    var conditions = new LinkedList<>(baseConditions);
                    conditions.add(new Condition.Equals("id", imageId));

                    var request = new Request(new Columns.All(), Condition.merge(conditions));
                    result.addAll(storage.getObjects(Image.class, request));
                }
            }

            if (!targetDeviceIds.isEmpty()) {
                for (Long deviceId : targetDeviceIds) {
                    var conditions = new LinkedList<>(baseConditions);
                    conditions.add(new Condition.Equals("deviceId", deviceId));

                    var request = new Request(new Columns.All(), Condition.merge(conditions));
                    result.addAll(storage.getObjects(Image.class, request));
                }
            }

            if (targetDeviceIds.isEmpty() && imageIds.isEmpty() && (from != null || to != null)) {
                var request = new Request(new Columns.All(), Condition.merge(baseConditions));
                result.addAll(storage.getObjects(Image.class, request));
            }

            return result;

        } else {

            var conditions = new LinkedList<Condition>();

            if (all) {
                if (permissionsService.notAdmin(getUserId())) {
                    conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
                }
            } else {
                if (userId == 0) {
                    conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
                } else {
                    permissionsService.checkUser(getUserId(), userId);
                    conditions.add(new Condition.Permission(User.class, userId, baseClass).excludeGroups());
                }
            }

            return storage.getObjects(baseClass, new Request(
                    new Columns.All(), Condition.merge(conditions)));

        }
    }

    @Override
    public Response add(Image entity) throws Exception {
        entity.setUploadedAt(new Date());
        return super.add(entity);
    }

    @Path("{id}/upload")
    @POST
    @Consumes("image/*")
    public Response uploadImage(
            @PathParam("id") long imageId, File file,
            @HeaderParam(HttpHeaders.CONTENT_TYPE) String type
    ) throws StorageException, IOException {


        Image image = storage.getObject(Image.class, new Request(
                new Columns.All(),
                new Condition.Equals("id", imageId)));
        if (image != null) {
            String name = image.getFileName();
            String extension = imageExtension(type);

            image.setFileExtension(extension);

            storage.updateObject(image, new Request(
                    new Columns.Include("fileExtension"),
                    new Condition.Equals("id", image.getId())));

            try (var input = new FileInputStream(file);
                 var output = mediaManager.createFileStream(Long.toString(image.getId()), name, extension)) {

                long transferred = 0;
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer, 0, buffer.length)) >= 0) {
                    output.write(buffer, 0, read);
                    transferred += read;
                    if (transferred > IMAGE_SIZE_LIMIT) {
                        throw new IllegalArgumentException("Image size limit exceeded");
                    }
                }
            }
            return Response.ok(name + "." + extension).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

}
