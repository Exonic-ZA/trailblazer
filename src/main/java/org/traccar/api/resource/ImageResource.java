package org.traccar.api.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.traccar.api.BaseObjectResource;
import org.traccar.database.MediaManager;
import org.traccar.model.Device;
import org.traccar.model.Image;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Path("images")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ImageResource extends BaseObjectResource<Image> {

    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int IMAGE_SIZE_LIMIT = 500000;

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

    @GET
    public Collection<Image> get(
            @QueryParam("all") boolean all, @QueryParam("userId") long userId,
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("id") List<Long> imageIds) throws StorageException {

        if (!deviceIds.isEmpty() || !imageIds.isEmpty()) {

            List<Image> result = new LinkedList<>();
            for (Long deviceId : deviceIds) {
                result.addAll(storage.getObjects(Image.class, new Request(
                        new Columns.All(),
                        new Condition.And(
                                new Condition.Equals("deviceId",  deviceId),
                                new Condition.Permission(User.class, getUserId(), Image.class)))));
            }
            for (Long imageId : imageIds) {
                result.addAll(storage.getObjects(Image.class, new Request(
                        new Columns.All(),
                        new Condition.And(
                                new Condition.Equals("id", imageId),
                                new Condition.Permission(User.class, getUserId(), Image.class)))));
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
