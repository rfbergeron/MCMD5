package com.flamingfrenchman.mcmd5.client.model;

import com.flamingfrenchman.mcmd5.Mcmd5;
import com.flamingfrenchman.mcmd5.proxy.ClientProxy;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.model.*;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.model.IModelPart;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.Models;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.model.animation.IClip;
import net.minecraftforge.common.model.animation.IJoint;
import net.minecraftforge.common.model.animation.IJointClip;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.fml.common.FMLLog;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public enum MD5Loader implements ICustomModelLoader {

    INSTANCE;

    private IResourceManager manager;

    private final Set<String> enabledDomains = new HashSet<>();
    private final Map<ResourceLocation, MD5Model> cache = new HashMap<>();

    public void addDomain(String domain)
    {
        enabledDomains.add(domain.toLowerCase());
        if(Mcmd5.debug)
            ((ClientProxy) Mcmd5.proxy).clientDebug(Level.INFO, "Domain " + domain + " registered for loader.");
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager)
    {
        this.manager = manager;
        cache.clear();
    }

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return enabledDomains.contains(modelLocation.getResourceDomain()) &&
                modelLocation.getResourcePath().endsWith(".dae");
    }

    @Override
    @MethodsReturnNonnullByDefault
    public IModel loadModel(ResourceLocation modelLocation) throws Exception {
        if(Mcmd5.debug)
            ((ClientProxy) Mcmd5.proxy).clientDebug(Level.INFO, "attempting to load model " + modelLocation.getResourcePath());
        ResourceLocation file = new ResourceLocation(modelLocation.getResourceDomain(), modelLocation.getResourcePath());
        if (!cache.containsKey(file))
        {
            IResource resource = null;
            try
            {
                try
                {
                    resource = manager.getResource(file);
                }
                catch (FileNotFoundException e)
                {
                    ((ClientProxy) Mcmd5.proxy).clientDebug(Level.INFO, "model failed to load; trying other locations");
                    if (modelLocation.getResourcePath().startsWith("models/block/"))
                        resource = manager.getResource(new ResourceLocation(file.getResourceDomain(), "models/item/" + file.getResourcePath().substring("models/block/".length())));
                    else if (modelLocation.getResourcePath().startsWith("models/item/"))
                        resource = manager.getResource(new ResourceLocation(file.getResourceDomain(), "models/block/" + file.getResourcePath().substring("models/item/".length())));
                    else throw e;
                }
                MD5Model.Parser parser = new MD5Model.Parser(resource.getInputStream(), manager, file);
                MD5Model model = null;
                try
                {
                    model = parser.parse();
                    cache.put(file, model);
                }
                catch (Exception e)
                {
                    cache.put(file, null);
                    throw e;
                }
            }
            finally
            {
                IOUtils.closeQuietly(resource);
            }
        }

        MD5Model model = cache.get(file);
        if(model == null) throw new ModelLoaderRegistry.LoaderException("Error loading model previously: " + file);
        return new ModelWrapper(modelLocation, model, true, true, 0);
    }

    private static final class MD5State implements  IModelState {
        /*
         * Returns the transformation that needs to be applied to the specific part of the model.
         * Coordinate system is determined by the part type.
         * if no part is provided, global model transformation is returned.
         */
        public Optional<TRSRTransformation> apply(Optional<? extends IModelPart> part) {
            return null;
        }
        
        public MD5State getParent() { return null; }
    }

    private static final class ModelWrapper implements IModel {
        private final ResourceLocation modelLocation;
        private final MD5Model model;
        private final ImmutableList<WrappedMesh> meshes;
        private final ImmutableMap<String, ResourceLocation> textures;
        private final boolean smooth;
        private final boolean gui3d;
        private final int defaultKey;

        public ModelWrapper(ResourceLocation modelLocation, MD5Model model, boolean smooth, boolean gui3d, int defaultKey)
        {
            this.modelLocation = modelLocation;
            this.model = model;
            this.smooth = smooth;
            this.gui3d = gui3d;
            this.defaultKey = defaultKey;
            this.textures = buildTextures(model.getMeshes());
            this.meshes = processMeshes(model);
        }

        private static ImmutableMap<String, ResourceLocation> buildTextures(ImmutableList<MD5Model.Mesh> meshes)
        {
            ImmutableMap.Builder<String, ResourceLocation> builder = ImmutableMap.builder();

            for(MD5Model.Mesh mesh : meshes) {
                String path = mesh.getTexture();
                String location = getLocation(path);
                builder.put(path, new ResourceLocation(location));
            }
            return builder.build();
        }

        private static ImmutableList<WrappedMesh> processMeshes(MD5Model model) {

        }

        private static String getLocation(String path)
        {
            if(path.endsWith(".png")) path = path.substring(0, path.length() - ".png".length());
            return path;
        }

        public Collection<ResourceLocation> getTextures() {
            ImmutableList.Builder<ResourceLocation> locationBuilder = ImmutableList.builder();
            for(MD5Model.Mesh mesh : model.getMeshes()) {
                String key = mesh.getTexture();
                locationBuilder.add(new ResourceLocation(modelLocation.getResourceDomain(), key));
            }
            return locationBuilder.build();
        }

        @Override
        public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter)
        {
            ImmutableMap.Builder<String, TextureAtlasSprite> builder = ImmutableMap.builder();
            TextureAtlasSprite missing = bakedTextureGetter.apply(new ResourceLocation("missingno"));
            for(Map.Entry<String, ResourceLocation> e : textures.entrySet())
            {
                if(e.getValue().getResourcePath().startsWith("#"))
                {
                    FMLLog.log.fatal("unresolved texture '{}' for b3d model '{}'", e.getValue().getResourcePath(), modelLocation);
                    builder.put(e.getKey(), missing);
                }
                else
                {
                    Mcmd5.logger.log(Level.INFO, "adding sprite with path " + e.getValue().getResourcePath());
                    if(bakedTextureGetter.apply(new ResourceLocation(e.getValue().getResourcePath())) == null)
                        Mcmd5.logger.log(Level.INFO, "sprite not found, oopsie");
                    builder.put(e.getKey(), bakedTextureGetter.apply(new ResourceLocation(e.getValue().getResourcePath())));
                }
            }
            builder.put("missingno", missing);
            return new BakedWrapper(model.getMeshes(), model.getJoints(), state, smooth, gui3d, format, builder.build());
        }

        public IModelState getDefaultState() {
            return TRSRTransformation.identity();
        }

        public Optional<? extends IClip> getClip(String name) {
            return Optional.empty();
        }

        public IModel process(ImmutableMap<String, String> customData) {
            return this;
        }

        public IModel smoothLighting(boolean value) {
            return this;
        }

        public IModel gui3d(boolean value) {
            return this;
        }

        public IModel uvlock(boolean value) {
            return this;
        }

        public IModel retexture(ImmutableMap<String, String> textures) {
            return this;
        }
    }

    private static final class WrappedMesh {

    }

    private static final class WrappedVertex {
        private final Vector3f pos;
        private final Vector3f norm;
        private final Vector2f texCoords;
    }

    private static final class BakedWrapper implements IBakedModel {
        private final ImmutableList<MD5Model.Mesh> meshes;
        private final ImmutableList<MD5Model.Joint> joints;
        private final IModelState state;
        private final boolean smooth;
        private final boolean gui3d;
        private final VertexFormat format;
        private final ImmutableMap<String, TextureAtlasSprite> textures;

        // this should be the default state (animation/pose) for this model
        // not sure when this is first created
        private ImmutableList<BakedQuad> quads;

        public BakedWrapper(ImmutableList<MD5Model.Mesh> meshes, ImmutableList<MD5Model.Joint> joints, IModelState state,
                            boolean smooth, boolean gui3d, VertexFormat format, ImmutableMap<String, TextureAtlasSprite> textures) {
            this.meshes = meshes;
            this.joints = joints;
            this.state = state;
            this.smooth = smooth;
            this.gui3d = gui3d;
            this.format = format;
            this.textures = textures;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand)
        {
            if(side != null) return ImmutableList.of();
            IModelState modelState = this.state;
            if(state instanceof IExtendedBlockState)
            {
                IExtendedBlockState exState = (IExtendedBlockState)state;
                if(exState.getUnlistedNames().contains(Properties.AnimationProperty))
                {
                    // FIXME: should animation state handle the parent state, or should it remain here?
                    IModelState parent = this.state;
                    if(parent instanceof MD5State)
                    {
                        MD5State ps = (MD5State) parent;
                        parent = ps.getParent();
                    }
                    IModelState newState = exState.getValue(Properties.AnimationProperty);
                    if(newState != null)
                    {
                        if (parent == null)
                        {
                            modelState = newState;
                        }
                        else
                        {
                            modelState = new ModelStateComposition(parent, newState);
                        }
                    }
                }
            }
            if(quads == null)
            {
                ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
                generateQuads(builder, meshes, this.state, ImmutableList.of());
                quads = builder.build();
            }
            // TODO: caching?
            if(this.state != modelState)
            {
                ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
                generateQuads(builder, meshes, modelState, ImmutableList.of());
                return builder.build();
            }
            return quads;
        }

        private void generateQuads(ImmutableList.Builder<BakedQuad> builder, ImmutableList<MD5Model.Mesh> meshes, final IModelState state, ImmutableList<String> path)
        {
            ImmutableList.Builder<String> pathBuilder = ImmutableList.builder();
            pathBuilder.addAll(path);
            pathBuilder.add(node.getId());
            ImmutableList<String> newPath = pathBuilder.build();
            for(MD5Model.Node child : node.getChildren()) {
                generateQuads(builder, child, state, newPath);
            }

            for(MD5Model.InstanceGeometry geometry : node.getGeometry()) {
                if(!state.apply(Optional.of(Models.getHiddenModelPart(newPath))).isPresent()) {
                    MD5Model.Mesh mesh = geometry.getInstance().mesh;
                    Collection<MD5Model.Triangle> triangles = mesh.bake(new Function<MD5Model.Node, Matrix4f>()
                    {
                        private final TRSRTransformation global = state.apply(Optional.empty()).orElse(TRSRTransformation.identity());
                        private final LoadingCache<MD5Model.Node, TRSRTransformation> localCache = CacheBuilder.newBuilder()
                                .maximumSize(32)
                                .build(new CacheLoader<MD5Model.Node, TRSRTransformation>()
                                {
                                    @Override
                                    public TRSRTransformation load(MD5Model.Node node) throws Exception
                                    {
                                        return state.apply(Optional.of(new MD5Joint(node))).orElse(TRSRTransformation.identity());
                                    }
                                });

                        @Override
                        public Matrix4f apply(MD5Model.Node node)
                        {
                            return global.compose(localCache.getUnchecked(node)).getMatrix();
                        }
                    });
                    for(MD5Model.Triangle t : triangles)
                    {
                        UnpackedBakedQuad.Builder quadBuilder = new UnpackedBakedQuad.Builder(format);
                        quadBuilder.setContractUVs(true);
                        quadBuilder.setQuadOrientation(EnumFacing.getFacingFromVector(t.getNormal().x, t.getNormal().y, t.getNormal().z));
                        List<MD5Model.Image> textures = null;
                        // the following code assumes one textures per instance_geometry entry.
                        // need to study spec further to determine if this is always the case.
                        if(geometry.getMaterial() != null) textures = geometry.getMaterial().effect.getImages();
                        TextureAtlasSprite sprite;
                        if(textures == null || textures.isEmpty()) sprite = ModelLoader.White.INSTANCE;
                        else if(textures.get(0) == MD5Model.Image.White) sprite = ModelLoader.White.INSTANCE;
                        else sprite = this.textures.get(textures.get(0).getPath());
                        quadBuilder.setTexture(sprite);
                        putVertexData(quadBuilder, t.getV1(), t.getV1().normal, sprite);
                        putVertexData(quadBuilder, t.getV2(), t.getV2().normal, sprite);
                        putVertexData(quadBuilder, t.getV3(), t.getV3().normal, sprite);
                        putVertexData(quadBuilder, t.getV3(), t.getV3().normal, sprite);
                        builder.add(quadBuilder.build());
                    }
                }
            }
        }

        private final void putVertexData(UnpackedBakedQuad.Builder builder, MD5Model.Vertex v, Vector3f faceNormal, TextureAtlasSprite sprite)
        {
            // TODO handle everything not handled (texture transformations, bones, transformations, normals, e.t.c)

            for(int e = 0; e < format.getElementCount(); e++)
            {
                switch(format.getElement(e).getUsage())
                {
                    case POSITION:
                        builder.put(e, v.getPos().x, v.getPos().y, v.getPos().z, 1);
                        break;
                    case COLOR:
                        if(v.getColor() != null)
                        {
                            builder.put(e, v.getColor().x, v.getColor().y, v.getColor().z, v.getColor().w);
                        }
                        else
                        {
                            builder.put(e, 1, 1, 1, 1);
                        }
                        break;
                    case UV:
                        // TODO handle more brushes
                        if(format.getElement(e).getIndex() < v.getTexCoords().length)
                        {
                            if(sprite == null) Mcmd5.logger.log(Level.INFO, "sprite is null");

                            builder.put(e,
                                    sprite.getInterpolatedU(v.getTexCoords()[format.getElement(e).getIndex()].x * 16),
                                    sprite.getInterpolatedV(v.getTexCoords()[format.getElement(e).getIndex()].y * 16),
                                    0,
                                    1
                            );
                        }
                        else
                        {
                            builder.put(e, 0, 0, 0, 1);
                        }
                        break;
                    case NORMAL:
                        if(v.getNormal() != null)
                        {
                            builder.put(e, v.getNormal().x, v.getNormal().y, v.getNormal().z, 0);
                        }
                        else
                        {
                            builder.put(e, faceNormal.x, faceNormal.y, faceNormal.z, 0);
                        }
                        break;
                    default:
                        builder.put(e);
                }
            }
        }

        @Override
        public boolean isAmbientOcclusion()
        {
            return smooth;
        }

        @Override
        public boolean isGui3d()
        {
            return gui3d;
        }

        @Override
        public boolean isBuiltInRenderer()
        {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture()
        {
            // FIXME somehow specify particle texture in the model
            return textures.values().asList().get(0);
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType)
        {
            return PerspectiveMapWrapper.handlePerspective(this, state, cameraTransformType);
        }

        @Override
        public ItemOverrideList getOverrides()
        {
            // TODO handle items
            return ItemOverrideList.NONE;
        }
    }

    // use for nodes defined in library_nodes; not sure if animation is hierarchical or
    // also local transforms
    static final class MD5JointClip implements IJointClip {
        public TRSRTransformation apply(float time) {
            return null;
        }
    }

    static final class MD5Joint implements IJoint {
        private final MD5Model.Node node;

        public MD5Joint(MD5Model.Node node) { this.node = node; }

        @Override
        public TRSRTransformation getInvBindPose()
        {
            Matrix4f m = new TRSRTransformation(node.getMatrix()).getMatrix();
            m.invert();
            TRSRTransformation pose = new TRSRTransformation(m);

            if(node.getParent() != null)
            {
                TRSRTransformation parent = new MD5Joint(node.getParent()).getInvBindPose();
                pose = pose.compose(parent);
            }
            return pose;
        }

        public Optional<? extends IJoint> getParent(){
            if(node.getParent() == null) return Optional.empty();
            else return Optional.of(new MD5Joint(node.getParent()));
        }

    }
}

