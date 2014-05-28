package com.bumptech.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import com.bumptech.glide.loader.bitmap.model.ModelLoader;
import com.bumptech.glide.resize.Metadata;
import com.bumptech.glide.resize.Priority;
import com.bumptech.glide.resize.RequestContext;
import com.bumptech.glide.resize.ResourceDecoder;
import com.bumptech.glide.resize.bitmap.CenterCrop;
import com.bumptech.glide.resize.bitmap.FitCenter;
import com.bumptech.glide.resize.load.BitmapDecoder;
import com.bumptech.glide.resize.load.DecodeFormat;
import com.bumptech.glide.resize.load.Downsampler;
import com.bumptech.glide.resize.load.MultiTransformation;
import com.bumptech.glide.resize.load.Transformation;
import com.bumptech.glide.resize.load.VideoBitmapDecoder;
import com.bumptech.glide.resize.request.BitmapRequestBuilder;
import com.bumptech.glide.resize.request.MultiTypeRequestCoordinator;
import com.bumptech.glide.resize.request.Request;
import com.bumptech.glide.resize.request.RequestCoordinator;
import com.bumptech.glide.resize.request.ThumbnailRequestCoordinator;
import com.bumptech.glide.resize.target.ImageViewTarget;
import com.bumptech.glide.resize.target.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic class that can handle loading a bitmap either from an image or as a thumbnail from a video given
 * models loaders to translate a model into generic resources for either an image or a video and decoders that can
 * decode those resources into bitmaps.
 *
 * @param <ModelType> The type of model representing the image or video.
 * @param <ImageResourceType> The resource type that the image {@link ModelLoader} will provide that can be decoded
 *                           by the image {@link BitmapDecoder}.
 * @param <VideoResourceType> The resource type that the video {@link ModelLoader} will provide that can be decoded
 *                           by the video {@link BitmapDecoder}.
 */
public class GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> {
    protected final Context context;
    private final List<Transformation<Bitmap>> transformations = new ArrayList<Transformation<Bitmap>>();
    private final ModelType model;
    private final RequestContext requestContext;
    private final Class<ImageResourceType> imageType;
    private final Class<VideoResourceType> videoType;
    private int animationId;
    private Animation animation;
    private int placeholderId;
    private int errorId;
    private RequestListener<ModelType> requestListener;
    private Float thumbSizeMultiplier;
    private GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> thumbnailRequestBuilder;
    private Float sizeMultiplier = 1f;
    private DecodeFormat decodeFormat = DecodeFormat.PREFER_RGB_565;
    private Drawable placeholderDrawable;
    private Drawable errorPlaceholder;
    private Priority priority = null;

    public GenericRequestBuilder(Context context, ModelType model, RequestContext requestContext,
            Class<ImageResourceType> imageType, Class<VideoResourceType> videoType) {
        this.imageType = imageType;
        this.videoType = videoType;
        if (context == null) {
            throw new NullPointerException("Context can't be null");
        }
        this.context = context;

        if (requestContext == null) {
            throw new NullPointerException("RequestContext can't be null");
        }

        this.requestContext = requestContext;

        this.model = model;
    }

    /**
     * Loads and displays the image retrieved by the given thumbnail request if it finishes before this request.
     * Best used for loading thumbnail images that are smaller and will be loaded more quickly than the fullsize
     * image. There are no guarantees about the order in which the requests will actually finish. However, if the
     * thumb request completes after the full request, the thumb image will never replace the full image.
     *
     * @see #thumbnail(float)
     *
     * <p>
     *     Note - Any options on the main request will not be passed on to the thumbnail request. For example, if
     *     you want an animation to occur when either the full image loads or the thumbnail loads, you need to call
     *     {@link #animate(int)} on both the thumb and the full request. For a simpler thumbnail option, see
     *     {@link #thumbnail(float)}.
     * </p>
     *
     * <p>
     *     Only the thumbnail call on the main request will be obeyed.
     * </p>
     *
     * @param thumbnailRequest The request to use to load the thumbnail.
     * @return This builder object.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> thumbnail(
            GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> thumbnailRequest) {
        this.thumbnailRequestBuilder = thumbnailRequest;

        return this;
    }

    /**
     * Loads an image in an identical manner to this request except with the dimensions of the target multiplied
     * by the given size multiplier. If the thumbnail load completes before the fullsize load, the thumbnail will
     * be shown. If the thumbnail load completes afer the fullsize load, the thumbnail will not be shown.
     *
     * <p>
     *     Note - The thumbnail image will be smaller than the size requested so the target (or {@link ImageView})
     *     must be able to scale the thumbnail appropriately. See {@link ImageView.ScaleType}.
     * </p>
     *
     * <p>
     *     Almost all options will be copied from the original load, including the {@link ModelLoader},
     *     {@link BitmapDecoder}, and {@link Transformation}s. However, {@link #placeholder(int)} and
     *     {@link #error(int)}, and {@link #listener(RequestListener)} will only be used on the fullsize load and
     *     will not be copied for the thumbnail load.
     * </p>
     *
     * <p>
     *     Only the thumbnail call on the main request will be obeyed.
     * </p>
     *
     * @param sizeMultiplier The multiplier to apply to the {@link Target}'s dimensions when loading the thumbnail.
     * @return This builder object.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> thumbnail(float sizeMultiplier) {
        if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
            throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
        }
        this.thumbSizeMultiplier = sizeMultiplier;

        return this;
    }

    /**
     * Applies a multiplier to the {@link Target}'s size before loading the image. Useful for loading thumbnails
     * or trying to avoid loading huge bitmaps on devices with overly dense screens.
     *
     * @param sizeMultiplier The multiplier to apply to the {@link Target}'s dimensions when loading the image.
     * @return This builder object.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> sizeMultiplier(
            float sizeMultiplier) {
        if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
            throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
        }
        this.sizeMultiplier = sizeMultiplier;

        return this;
    }

    /**
     * Loads the image from the given resource type into an {@link Bitmap} using the given {@link BitmapDecoder}.
     *
     * <p>
     *     Will be ignored if the data represented by the given model is not an image.
     * </p>
     *
     * @see Downsampler
     *
     * @param decoder The {@link BitmapDecoder} to use to decode the image resource.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> imageDecoder(
            ResourceDecoder<ImageResourceType, Bitmap> decoder) {
        requestContext.register(decoder, imageType, Bitmap.class);

        return this;
    }

    /**
     * Loads the video from the given resource type into an {@link Bitmap} using the given {@link BitmapDecoder}.
     *
     * <p>
     *     Will be ignored if the data represented by the given model is not a video.
     * </p>
     *
     * @see VideoBitmapDecoder
     *
     * @param decoder The {@link BitmapDecoder} to use to decode the video resource.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> videoDecoder(
            ResourceDecoder<VideoResourceType, Bitmap> decoder) {
        requestContext.register(decoder, videoType, Bitmap.class);

        return this;
    }

    /**
     * Sets the preferred format for {@link Bitmap}s decoded in this request. Defaults to
     * {@link DecodeFormat#PREFER_RGB_565}.
     *
     * <p>
     *     Note - If using a {@link Transformation} that expect bitmaps to support transparency, this should always be
     *     set to ALWAYS_ARGB_8888. RGB_565 requires fewer bytes per pixel and is generally preferable, but it does not
     *     support transparency.
     * </p>
     *
     * @see DecodeFormat
     *
     * @param format The format to use.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> format(DecodeFormat format) {
        this.decodeFormat = format;

        return this;
    }

    /**
     * Sets the priority for this load.
     *
     * @param priority A priority.
     * @return This request builder.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> priority(Priority priority) {
        this.priority = priority;

        return this;
    }

    /**
     * Transform images using {@link CenterCrop}.
     *
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> centerCrop() {
        return transform(new CenterCrop(Glide.get(context).getBitmapPool()));
    }

    /**
     * Transform images using {@link FitCenter}.
     *
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> fitCenter() {
        return transform(new FitCenter(Glide.get(context).getBitmapPool()));
    }

    /**
     * Transform images with the given {@link Transformation}. Appends this transformation onto any existing
     * transformations
     *
     * @param transformation the transformation to apply.
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> transform(
            Transformation<Bitmap> transformation) {
        transformations.add(transformation);

        return this;
    }

    /**
     * Sets an animation to run on the wrapped target when an image load finishes. Will only be run if the image
     * was loaded asynchronously (ie was not in the memory cache)
     *
     * @param animationId The resource id of the animation to run
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> animate(int animationId) {
        this.animationId = animationId;

        return this;
    }

    /**
     * Sets an animation to run on the wrapped target when an image load finishes. Will only be run if the image
     * was loaded asynchronously (ie was not in the memory cache)
     *
     * @param animation The animation to run
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> animate(Animation animation) {
        this.animation = animation;

        return this;
    }

    /**
     * Sets a resource to display while an image is loading
     *
     * @param resourceId The id of the resource to use as a placeholder
     * @return This RequestBuilder
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> placeholder(int resourceId) {
        this.placeholderId = resourceId;

        return this;
    }

    /**
     * Sets a drawable to display while an image is loading.
     *
     * @param drawable The drawable to display as a placeholder.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> placeholder(Drawable drawable) {
        this.placeholderDrawable = drawable;

        return this;
    }

    /**
     * Sets a resource to display if a load fails
     *
     * @param resourceId The id of the resource to use as a placeholder
     * @return This request
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> error(int resourceId) {
        this.errorId = resourceId;

        return this;
    }

    /**
     * Sets a {@link Drawable} to display if a load fails.
     *
     * @param drawable The drawable to display.
     * @return This RequestBuilder.
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> error(Drawable drawable) {
        this.errorPlaceholder = drawable;

        return this;
    }

    /**
     * Sets a RequestBuilder listener to monitor the image load. It's best to create a single instance of an
     * exception handler per type of request (usually activity/fragment) rather than pass one in per request to
     * avoid some redundant object allocation.
     *
     * @param requestListener The request listener to use
     * @return This request
     */
    public GenericRequestBuilder<ModelType, ImageResourceType, VideoResourceType> listener(
            RequestListener<ModelType> requestListener) {
        this.requestListener = requestListener;

        return this;
    }

    /**
     * Set the target the image will be loaded into.
     *
     * @param target The target to load te image for
     * @return The given target.
     */
    public <Y extends Target> Y into(Y target) {
        Request previous = target.getRequest();
        if (previous != null) {
            previous.clear();
        }

        Request request = buildRequest(target);
        target.setRequest(request);
        if (request != null) {
            request.run();
        }
        return target;
    }

    /**
     * Sets the {@link ImageView} the image will be loaded into, cancels any existing loads into the view, and frees
     * any resources Glide has loaded into the view so they may be reused.
     *
     * @see Glide#clear(View)
     *
     * @param view The view to cancel previous loads for and load the new image into.
     * @return The {@link ImageViewTarget} used to wrap the given {@link ImageView}.
     */
    public ImageViewTarget into(ImageView view) {
        return into(new ImageViewTarget(view));
    }

    private <Y extends Target> Request buildRequest(Y target) {
        final Request result;
        if (priority == null) {
            priority = Metadata.DEFAULT.getPriority();
        }

        if (thumbnailRequestBuilder != null) {
            ThumbnailRequestCoordinator requestCoordinator = new ThumbnailRequestCoordinator();
            Request fullRequest = buildBitmapRequest(target, sizeMultiplier, priority, requestCoordinator);

            if (thumbnailRequestBuilder.animationId <= 0) {
                thumbnailRequestBuilder.animationId = animationId;
            }

            if (thumbnailRequestBuilder.animation == null) {
                thumbnailRequestBuilder.animation = animation;
            }

            if (thumbnailRequestBuilder.requestListener == null && requestListener != null) {
                thumbnailRequestBuilder.requestListener = requestListener;
            }

            if (thumbnailRequestBuilder.priority == null) {
                thumbnailRequestBuilder.priority = getThumbnailPriority();
            }

            Request thumbnailRequest = thumbnailRequestBuilder.buildBitmapRequest(target,
                    thumbnailRequestBuilder.sizeMultiplier, thumbnailRequestBuilder.priority, requestCoordinator);

            requestCoordinator.setRequests(fullRequest, thumbnailRequest);
            result = requestCoordinator;
        } else if (thumbSizeMultiplier != null) {
            ThumbnailRequestCoordinator requestCoordinator = new ThumbnailRequestCoordinator();
            Request fullRequest = buildBitmapRequest(target, sizeMultiplier, priority, requestCoordinator);
            Request thumbnailRequest = buildBitmapRequest(target, thumbSizeMultiplier, getThumbnailPriority(),
                    requestCoordinator);
            requestCoordinator.setRequests(fullRequest, thumbnailRequest);
            result = requestCoordinator;
        } else {
            result = buildBitmapRequest(target, sizeMultiplier, priority, null);
        }
        return result;
    }

    private Priority getThumbnailPriority() {
        final Priority result;
        if (priority == Priority.LOW) {
            result = Priority.NORMAL;
        } else if (priority == Priority.NORMAL) {
            result = Priority.HIGH;
        } else {
            result = Priority.IMMEDIATE;
        }
        return result;
    }

    private <Y extends Target> Request buildBitmapRequest(Y target, float sizeMultiplier, Priority priority,
            RequestCoordinator requestCoordinator) {
        if (imageType == null) {
            return buildBitmapRequestForType(target, videoType, sizeMultiplier, priority, requestCoordinator).build();
        } else if (videoType == null) {
            return buildBitmapRequestForType(target, imageType, sizeMultiplier, priority, requestCoordinator).build();
        } else {
            MultiTypeRequestCoordinator typeCoordinator = new MultiTypeRequestCoordinator(requestCoordinator);
            Request imageRequest =
                    buildBitmapRequestForType(target, imageType, sizeMultiplier, priority, typeCoordinator).build();
            Request videoRequest =
                    buildBitmapRequestForType(target, videoType, sizeMultiplier, priority, typeCoordinator).build();
            typeCoordinator.setRequests(imageRequest, videoRequest);
            return typeCoordinator;
        }
    }

    private <Y extends Target, Z> BitmapRequestBuilder<ModelType, Z> buildBitmapRequestForType(Y target,
            Class<Z> resourceClass, float sizeMultiplier, Priority priority, RequestCoordinator requestCoordinator) {
        return new BitmapRequestBuilder<ModelType, Z>(resourceClass)
                .setContext(context)
                .setPriority(priority)
                .setImageManager(Glide.get(context).getImageManager())
                .setEngine(Glide.get(context).getEngine())
                .setRequestContext(requestContext)
                .setModel(model)
                .setTarget(target)
                .setDecodeFormat(decodeFormat)
                .setAnimation(animationId)
                .setAnimation(animation)
                .setRequestListener(requestListener)
                .setPlaceholderResource(placeholderId)
                .setPlaceholderDrawable(placeholderDrawable)
                .setErrorResource(errorId)
                .setErrorDrawable(errorPlaceholder)
                .setSizeMultiplier(sizeMultiplier)
                .setPriority(priority)
                .setTransformation(getFinalTransformation())
                .setRequestCoordinator(requestCoordinator);
    }

    @SuppressWarnings("unchecked")
    private Transformation<Bitmap> getFinalTransformation() {
        switch (transformations.size()) {
            case 0:
                return Transformation.NONE;
            case 1:
                return transformations.get(0);
            default:
                return new MultiTransformation<Bitmap>(transformations);
        }
    }
}
