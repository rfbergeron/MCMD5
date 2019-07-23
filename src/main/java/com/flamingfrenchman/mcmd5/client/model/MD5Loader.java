package com.flamingfrenchman.mcmd5.client.model;

import com.flamingfrenchman.mcmd5.Mcmd5;
import com.flamingfrenchman.mcmd5.proxy.ClientProxy;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.model.*;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.model.IModelPart;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.model.animation.IClip;
import net.minecraftforge.common.model.animation.IJoint;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.fml.common.FMLLog;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import scala.Int;

import javax.annotation.Nullable;
import javax.vecmath.*;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public enum MD5Loader implements ICustomModelLoader {

    INSTANCE;

    private IResourceManager manager;

    private final Set<String> enabledDomains = new HashSet<>();
    private final Map<ResourceLocation, MD5Model> cache = new HashMap<>();

    public static void log(String str) {
        Mcmd5.logger.log(Level.INFO, str);
    }

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
                (modelLocation.getResourcePath().endsWith(".md5mesh") ||
                 modelLocation.getResourcePath().endsWith(".md5anim"));
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
                MD5Model.Parser parser = new MD5Model.Parser(resource, manager, file);
                MD5Model model;
                try
                {
                    model = parser.parse();
                    cache.put(file, model);
                }
                catch (NullPointerException e) {
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

    /*
     * Represents the dynamic information associated with the model.
     * Common use case is (possibly interpolated) animation frame.
     */
    private static final class MD5State implements IModelState {
        @Nullable
        private final Animation animation;
        private final int frame;
        private final int nextFrame;
        private final float progress;
        @Nullable
        private final IModelState parent;

        public MD5State(@Nullable Animation animation, int frame)
        {
            this(animation, frame, frame, 0);
        }

        public MD5State(@Nullable Animation animation, int frame, IModelState parent)
        {
            this(animation, frame, frame, 0, parent);
        }

        public MD5State(@Nullable Animation animation, int frame, int nextFrame, float progress)
        {
            this(animation, frame, nextFrame, progress, null);
        }

        public MD5State(@Nullable Animation animation, int frame, int nextFrame, float progress, @Nullable IModelState parent)
        {
            this.animation = animation;
            this.frame = frame;
            this.nextFrame = nextFrame;
            this.progress = MathHelper.clamp(progress, 0, 1);
            this.parent = getParent(parent);
        }

        @Nullable
        private IModelState getParent(@Nullable IModelState parent)
        {
            if (parent == null) return null;
            else if (parent instanceof MD5State) return ((MD5State)parent).parent;
            return parent;
        }

        @Nullable
        public Animation getAnimation()
        {
            return animation;
        }

        public int getFrame()
        {
            return frame;
        }

        public int getNextFrame()
        {
            return nextFrame;
        }

        public float getProgress()
        {
            return progress;
        }

        @Nullable
        public IModelState getParent()
        {
            return parent;
        }

        @Override
        public Optional<TRSRTransformation> apply(Optional<? extends IModelPart> part)
        {
            // TODO make more use of Optional
            if(!part.isPresent())
            {
                if(parent != null)
                {
                    return parent.apply(part);
                }
                return Optional.empty();
            }
            if(part.get() instanceof ItemCameraTransforms.TransformType) {
                ItemCameraTransforms.TransformType type = (ItemCameraTransforms.TransformType) part.get();
                TRSRTransformation cameraTransform;
                if(type == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND) {
                    log("its in the right hand");
                    cameraTransform = new TRSRTransformation(new Vector3f(-4, -4, 4),
                            null, new Vector3f(0.1f, 0.1f, 0.1f), null);
                    return Optional.of(cameraTransform);
                }
                return Optional.empty();
            }
            if(!(part.get() instanceof WrappedJoint))
            {
                return Optional.empty();
            }

            TRSRTransformation nodeTransform;

            if(progress < 1e-5 || frame == nextFrame)
            {
                nodeTransform = getNodeMatrix(part.get(), frame);
            }
            else if(progress > 1 - 1e-5)
            {
                nodeTransform = getNodeMatrix(part.get(), nextFrame);
            }
            else
            {
                nodeTransform = getNodeMatrix(part.get(), frame);
                nodeTransform = nodeTransform.slerp(getNodeMatrix(part.get(), nextFrame), progress);
            }
            if(parent != null && ((WrappedJoint)part.get()).getParent() == null)
            {
                return Optional.of(parent.apply(part).orElse(TRSRTransformation.identity()).compose(nodeTransform));
            }
            return Optional.of(nodeTransform);
        }

        /*private Optional<TRSRTransformation> applyCameraTransforms(Optional<? extends IModelPart> part) {
            if(!(part.get() instanceof ItemCameraTransforms.TransformType)) {
                return Optional.empty();
            }
            TRSRTransformation cameraTransform = getNodeMatrix(part.get(), )
        }*/

        private static LoadingCache<Triple<Animation, IModelPart, Integer>, TRSRTransformation> cache = CacheBuilder.newBuilder()
                .maximumSize(16384)
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .build(new CacheLoader<Triple<Animation, IModelPart, Integer>, TRSRTransformation>()
                {
                    @Override
                    public TRSRTransformation load(Triple<Animation, IModelPart, Integer> key) throws Exception
                    {
                        return getNodeMatrix(key.getLeft(), key.getMiddle(), key.getRight());
                    }
                });

        public TRSRTransformation getNodeMatrix(IModelPart part)
        {
            return getNodeMatrix(part, frame);
        }

        public TRSRTransformation getNodeMatrix(IModelPart part, int frame)
        {
            return cache.getUnchecked(Triple.of(animation, part, frame));
        }

        public static TRSRTransformation getNodeMatrix(@Nullable Animation animation, IModelPart part, int frame)
        {
            TRSRTransformation ret = TRSRTransformation.identity();
            Key key = null;
            if(animation != null) key = animation.getKeys().get(frame, part);
            if(key != null)
            {
                Optional parent = ((WrappedJoint)part).getParent();

                if(parent.isPresent())
                {
                    IJoint parentJoint = (IJoint) parent.get();
                    // parent model-global current pose
                    TRSRTransformation pm = cache.getUnchecked(Triple.of(animation, parentJoint, frame));
                    ret = ret.compose(pm);
                }
                // current node local pose
                ret = ret.compose(new TRSRTransformation(key.getPos(), key.getRot(), key.getScale(), null));

                // TODO cache
                TRSRTransformation invBind = ((IJoint) part).getInvBindPose();
                ret = ret.compose(invBind);
            }
            return ret;
        }
    }

    private static final class StaticState implements IModelState {
        private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;

        public StaticState(ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms) {
            this.transforms = transforms;
        }

        public Optional<TRSRTransformation> apply(Optional<? extends IModelPart> part) {
            if(!part.isPresent())
            {
                return Optional.empty();
            }
            if(part.get() instanceof ItemCameraTransforms.TransformType) {
                ItemCameraTransforms.TransformType type = (ItemCameraTransforms.TransformType) part.get();
                TRSRTransformation cameraTransform = transforms.getOrDefault(type, TRSRTransformation.identity());
                return Optional.of(cameraTransform);
            }
            return Optional.empty();
        }
    }

    private static final class Animation
    {
        private final int flags;
        private final int frames;
        private final float fps;
        // int is frame, part is the target joint
        private final ImmutableTable<Integer, IModelPart, Key> keys;

        public Animation(int flags, int frames, float fps, ImmutableTable<Integer, IModelPart, Key> keys)
        {
            this.flags = flags;
            this.frames = frames;
            this.fps = fps;
            this.keys = keys;
            /*for(Table.Cell<Integer, IModelPart, Key> cell: keys.cellSet()) {
                log("frame: " + cell.getRowKey().toString() + " part: " + cell.getColumnKey().toString() + "parent: " + ((IJoint) cell.getColumnKey()).getParent() + " key: " + cell.getValue().toString());
            }*/
        }

        public int getFlags()
        {
            return flags;
        }

        public int getFrames()
        {
            return frames;
        }

        public float getFps()
        {
            return fps;
        }

        public ImmutableTable<Integer, IModelPart, Key> getKeys()
        {
            return keys;
        }
    }

    public static final class Key
    {
        @Nullable
        private final Vector3f pos;
        @Nullable
        private final Vector3f scale;
        @Nullable
        private final Quat4f rot;

        public Key(@Nullable Vector3f pos, @Nullable Vector3f scale, @Nullable Quat4f rot)
        {
            this.pos = pos;
            this.scale = scale;
            this.rot = rot;
        }

        @Nullable
        public Vector3f getPos()
        {
            return pos;
        }

        @Nullable
        public Vector3f getScale()
        {
            return scale;
        }

        @Nullable
        public Quat4f getRot()
        {
            return rot;
        }

        @Override
        public String toString()
        {
            return String.format("Key [pos=%s, scale=%s, rot=%s]", pos, scale, rot);
        }
    }

    private static final class ModelWrapper implements IModel {
        private final ResourceLocation modelLocation;
        private final MD5Model model;
        private final ImmutableList<WrappedMesh> meshes;
        private final ImmutableMap<String, ResourceLocation> textures;
        private final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;
        private final IModelState state;
        private final boolean smooth;
        private final boolean gui3d;
        private final int defaultKey;
        private ImmutableList<WrappedJoint> joints;

        /*public ModelWrapper(ResourceLocation modelLocation, MD5Model model, ImmutableList<WrappedJoint> animJoints,
                            boolean smooth, boolean gui3d, int defaultKey)
        {
            this.modelLocation = modelLocation;
            this.model = model;
            this.joints = animJoints;
            this.smooth = smooth;
            this.gui3d = gui3d;
            this.defaultKey = defaultKey;
            this.textures = buildTextures(modelLocation, model.getMeshes());
            this.meshes = process(model, animJoints);
            this.transforms = buildTransforms(model.getTransforms());
            this.state = buildState(model.getFrames(), joints);
        }*/

        public ModelWrapper(ResourceLocation modelLocation, MD5Model model, boolean smooth, boolean gui3d, int defaultKey) {
            this.modelLocation = modelLocation;
            this.model = model;
            this.joints = model.getAnimJoints() == null ? null : buildJoints(model.getAnimJoints());
            this.smooth = smooth;
            this.gui3d = gui3d;
            this.defaultKey = defaultKey;
            this.textures = buildTextures(modelLocation, model.getMeshes());
            this.meshes = process(model, joints);
            this.transforms = buildTransforms(model.getTransforms());
            this.state = buildState(model.getFrames(), joints, transforms);
        }

        private static ImmutableMap<String, ResourceLocation> buildTextures(ResourceLocation modelLocation, ImmutableList<MD5Model.MD5Mesh> meshes)
        {
            ImmutableMap.Builder<String, ResourceLocation> builder = ImmutableMap.builder();

            for(MD5Model.MD5Mesh mesh : meshes) {
                String path = mesh.getTexture();
                builder.put(path, new ResourceLocation(path));
            }
            return builder.build();
        }

        private static ImmutableList<WrappedMesh> process(MD5Model model, ImmutableList<WrappedJoint> animJoints) {
            ImmutableList.Builder<WrappedMesh> builder = ImmutableList.builder();
            for(MD5Model.MD5Mesh mesh : model.getMeshes()) {
                builder.add(generateWrappedMesh(model.getJoints(), mesh, animJoints));
            }
            return builder.build();
        }

        private static ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> buildTransforms(ImmutableList<MD5Model.MD5Transform> transforms) {
            ImmutableMap.Builder<ItemCameraTransforms.TransformType, TRSRTransformation> builder = ImmutableMap.builder();
            for(MD5Model.MD5Transform transform : transforms) {
                for(ItemCameraTransforms.TransformType type : ItemCameraTransforms.TransformType.values()) {
                    if(transform.getName().toUpperCase().equals(type.name())) {
                        builder.put(type, transform.getTRSRTransform());
                        break;
                    }
                }
            }
            return builder.build();
        }

        private static WrappedMesh generateWrappedMesh(ImmutableList<MD5Model.MD5Joint> joints, MD5Model.MD5Mesh mesh, ImmutableList<WrappedJoint> animJoints) {
            log("generating wrapped mesh");
            MD5Model.MD5Vertex[] vertices = mesh.getVertices();
            MD5Model.MD5Weight[] weights = mesh.getWeights();
            ImmutableList.Builder<WrappedVertex> vertexBuilder = ImmutableList.builder();
            ImmutableList.Builder<Integer> triangleBuilder = ImmutableList.builder();

            for (int j = 0 ; j < vertices.length ; ++j) {
                MD5Model.MD5Vertex vertex = vertices[j];

                int startWeight = vertex.getWeightStart();
                int numWeights = vertex.getNumweights();
                
                // transforms joints to their binding pose
                // the math still makes my brain hurt
                // i shouldve paid more attention in linear algebra
                for (int i = startWeight; i < startWeight + numWeights; i++) {
                    MD5Model.MD5Weight weight = weights[i];
                    MD5Model.MD5Joint joint = joints.get(weight.getJointIndex());

                    Vector3f weightPos = weight.getPos();
                    //extend pos to quat so it can be rotated
                    //Quat4f rotatedPos = joint.getRot();
                    Matrix4f m = new Matrix4f(joint.getRot(), joint.getPos(), 1.0F);
                    Vector4f acumPos = new Vector4f(weightPos.x, weightPos.y, weightPos.z, 1);
                    m.transform(acumPos);
                    acumPos.scale(weight.getBias());
                    vertex.addToPos(new Vector3f(acumPos.x, acumPos.y, acumPos.z));
                }
            }

            for (MD5Model.MD5Triangle tri : mesh.getTriangles()) {

                // Normals
                int i0 = tri.getV0();
                int i1 = tri.getV1();
                int i2 = tri.getV2();
                MD5Model.MD5Vertex v0 = vertices[i0];
                MD5Model.MD5Vertex v1 = vertices[i1];
                MD5Model.MD5Vertex v2 = vertices[i2];
                triangleBuilder.add(i0);
                triangleBuilder.add(i1);
                triangleBuilder.add(i2);

                // calculate triangle face normal as normal cross n2
                // add to vertex and normalize later
                Vector3f normal = (new Vector3f(v2.getPos()));
                normal.sub(v0.getPos());
                Vector3f n2 = (new Vector3f(v1.getPos()));
                n2.sub(v0.getPos());
                normal.cross(normal, n2);

                v0.addToNorm(normal);
                v1.addToNorm(normal);
                v2.addToNorm(normal);
            }

            log("computing bindings for mesh");
            for(MD5Model.MD5Vertex vertex : vertices) {
                // remember to normalize
                vertex.getNorm().normalize();
                ImmutableList.Builder<WrappedJoint> boundJoints = ImmutableList.builder();
                ImmutableList.Builder<Float> boundBiases = ImmutableList.builder();
                int weightStart = vertex.getWeightStart();
                int numWeights = vertex.getNumweights();
                log("   begin vertex");
                for(int i = 0 ; i < numWeights ; ++i) {
                    if(animJoints != null) {
                        MD5Model.MD5Weight w = weights[weightStart + i];
                        boundJoints.add(animJoints.get(w.getJointIndex()));
                        boundBiases.add(w.getBias());
                        log("\tweight " + (weightStart + i) + " joint: " + animJoints.get(w.getJointIndex()).name);
                    }
                }

                //log(vertex.toString());
                vertexBuilder.add(new WrappedVertex(vertex.getPos(), vertex.getNorm(), vertex.getTexCoords(),
                        boundJoints.build(), boundBiases.build()));
            }
            
            return new WrappedMesh(mesh.getTexture(), vertexBuilder.build(), triangleBuilder.build());
        }

        private static ImmutableList<WrappedJoint> buildJoints(ImmutableList<MD5Model.MD5AnimJoint> animJoints) {
            WrappedJoint[] wrappedJoints = new WrappedJoint[animJoints.size()];
            ImmutableList.Builder<WrappedJoint> builder = ImmutableList.builder();

            for(int i = 0 ; i < animJoints.size() ; ++i) {
                MD5Model.MD5AnimJoint animJoint = animJoints.get(i);
                if(animJoint.getParent() > -1)
                    wrappedJoints[i] = new WrappedJoint(animJoint.getPos(), animJoint.getRot(), wrappedJoints[animJoint.getParent()], animJoint.getName());
                else
                    wrappedJoints[i] = new WrappedJoint(animJoint.getPos(), animJoint.getRot(), null, animJoint.getName());

                log("joint: " + i + " " + animJoint.getName() + " rot: " + animJoint.getRot() + " pos: " + animJoint.getPos());
            }

            return builder.add(wrappedJoints).build();
        }

        private static IModelState buildState(ImmutableList<MD5Model.MD5Frame> frames, ImmutableList<WrappedJoint> joints, ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms) {
            if(joints == null) return new StaticState(transforms);

            ImmutableTable.Builder<Integer, IModelPart, Key> keys = ImmutableTable.builder();
            for(MD5Model.MD5Frame frame : frames) {
                Vector3f[] positions = frame.getPositions();
                Quat4f[] orientations = frame.getOrientations();
                for(int i = 0 ; i < joints.size() ; ++i) {
                    keys.put(frame.getNumber(), joints.get(i), new Key(positions[i], null, orientations[i]));
                }
            }
            return new MD5State(new Animation(0, frames.size(), 24, keys.build()), 0);
        }

        public Collection<ResourceLocation> getTextures() {
            ImmutableList.Builder<ResourceLocation> locationBuilder = ImmutableList.builder();
            for(MD5Model.MD5Mesh mesh : model.getMeshes()) {
                String loc = mesh.getTexture();
                locationBuilder.add(new ResourceLocation(loc));
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
                    FMLLog.log.fatal("unresolved texture '{}' for md5 model '{}'", e.getValue().getResourcePath(), modelLocation);
                    builder.put(e.getKey(), missing);
                }
                else
                {
                    if(bakedTextureGetter.apply(e.getValue()) == null) {
                        builder.put(e.getKey(), missing);
                    }
                    else {
                        builder.put(e.getKey(), bakedTextureGetter.apply(e.getValue()));
                    }
                }
            }
            builder.put("missingno", missing);
            return new BakedWrapper(meshes, state, smooth, gui3d, format, builder.build(), transforms);
        }

        public IModelState getDefaultState() {
            return this.state;
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
        private final String texture;
        private final ImmutableList<WrappedVertex> vertices;
        private final ImmutableList<Integer> triangles;

        public WrappedMesh(String texture, ImmutableList<WrappedVertex> vertices,
                           ImmutableList<Integer> triangles) {
            this.texture = texture;
            this.vertices = vertices;
            this.triangles = triangles;
        }

        public Vector3f getFaceNormal(int index) {
            int i1 = triangles.get(index);
            int i2 = triangles.get(index + 1);
            int i3 = triangles.get(index + 2);
            Vector3f v1 = vertices.get(i1).pos;
            Vector3f v2 = vertices.get(i2).pos;
            Vector3f v3 = vertices.get(i3).pos;

            Vector3f normal = new Vector3f(v3.x, v3.y, v3.z);
            Vector3f n1 = new Vector3f(v2.x, v2.y, v2.z);
            normal.sub(v1);
            n1.sub(v1);
            normal.cross(normal, n1);
            normal.normalize();
            return normal;
        }

        public WrappedMesh bake(Function<WrappedJoint, Matrix4f> animator)
        {
            ImmutableList.Builder<WrappedVertex> builder = ImmutableList.builder();
            for(WrappedVertex v : vertices)
            {
                builder.add(v.bake(animator));
            }
            return new WrappedMesh(this.texture, builder.build(), this.triangles);
        }
    }

    private static final class WrappedVertex {
        private final Vector3f pos;
        private final Vector3f norm;
        private final Vector2f texCoords;
        private final ImmutableList<WrappedJoint> joints;
        private final ImmutableList<Float> biases;

        public WrappedVertex(Vector3f pos, Vector3f norm, Vector2f texCoords,
                             ImmutableList<WrappedJoint> joints, ImmutableList<Float> biases) {
            this.pos = pos;
            this.norm = norm;
            this.texCoords = texCoords;
            this.joints = joints;
            this.biases = biases;
        }

        public WrappedVertex bake(Function<WrappedJoint, Matrix4f> animator) {
            Vector4f newPos = new Vector4f(0, 0, 0, 1);
            Vector4f newNorm = new Vector4f();

            //this isnt working
            for(int i = 0 ; i < joints.size() ; ++i) {
                Matrix4f m = animator.apply(joints.get(i));

                Vector4f tmpPos = new Vector4f(this.pos);
                tmpPos.w = 1;

                m.transform(tmpPos);
                tmpPos.scale(biases.get(i));
                newPos.add(tmpPos);

                Vector4f tmpNorm = new Vector4f(this.norm);

                m.transform(tmpNorm);
                tmpNorm.scale(biases.get(i));
                newNorm.add(tmpNorm);
            }

            Vector3f ret = new Vector3f(newNorm.x, newNorm.y, newNorm.z);
            ret.normalize();
            return new WrappedVertex(new Vector3f(newPos.x, newPos.y, newPos.z),
                    ret, this.texCoords, this.joints, this.biases);
        }

        public String toString() {
            return "vertex data: " + " " + pos.toString() + " " + norm.toString() + " " + texCoords.toString();
        }
    }

    public static final class WrappedJoint implements IJoint {
        private IJoint parent;
        private TRSRTransformation invBindPose;
        private String name;

        public WrappedJoint(Vector3f pos, Quat4f rot) {
            this.invBindPose = new TRSRTransformation(pos, rot, null, null).inverse();
            this.parent = null;
        }

        public WrappedJoint(Vector3f pos, Quat4f rot, IJoint parent) {
            this(pos, rot);
            this.parent = parent;
        }

        public WrappedJoint(Vector3f pos, Quat4f rot, IJoint parent, String name) {
            this(pos, rot, parent);
            this.name = name;
        }

        public WrappedJoint(TRSRTransformation invBindPose, IJoint parent) {
            this.invBindPose = invBindPose;
            this.parent = parent;
        }

        public TRSRTransformation getInvBindPose() {
            return this.invBindPose;
        }

        public Optional<? extends IJoint> getParent() {
            return parent == null ? Optional.empty() : Optional.of(parent);
        }

        public String toString() {
            return this.name;
        }
    }

    private static final class BakedWrapper implements IBakedModel {
        private final ImmutableList<WrappedMesh> meshes;
        private final IModelState state;
        private final boolean smooth;
        private final boolean gui3d;
        private final VertexFormat format;
        private final ImmutableMap<String, TextureAtlasSprite> textures;
        private ImmutableList<BakedQuad> quads;
        private ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms;

        public BakedWrapper(ImmutableList<WrappedMesh> meshes, IModelState state,
                            boolean smooth, boolean gui3d, VertexFormat format, ImmutableMap<String, TextureAtlasSprite> textures,
                            ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms) {
            this.meshes = meshes;
            this.state = state;
            this.smooth = smooth;
            this.gui3d = gui3d;
            this.format = format;
            this.textures = textures;
            this.transforms = transforms;
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
            if(this.state != modelState)
            {
                ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
                generateQuads(builder, meshes, modelState, ImmutableList.of());
                return builder.build();
            }
            return quads;
        }

        private void generateQuads(ImmutableList.Builder<BakedQuad> builder, ImmutableList<WrappedMesh> meshes, final IModelState state, ImmutableList<String> path)
        {
            for(WrappedMesh mesh : meshes) {
                // currently does nothing to the mesh since animation handling is not currently working

                WrappedMesh animMesh = mesh.bake(new Function<WrappedJoint, Matrix4f>()
                {
                    private final TRSRTransformation global = TRSRTransformation.identity();
                    //state.apply(Optional.empty()).orElse(TRSRTransformation.identity());
                    private final LoadingCache<WrappedJoint, TRSRTransformation> localCache = CacheBuilder.newBuilder()
                            .maximumSize(32)
                            .build(new CacheLoader<WrappedJoint, TRSRTransformation>()
                            {
                                @Override
                                public TRSRTransformation load(WrappedJoint j) throws Exception
                                {
                                    return state.apply(Optional.of(j)).orElse(TRSRTransformation.identity());
                                }
                            });
                    
                    @Override
                    public Matrix4f apply(WrappedJoint j)
                    {
                        return global.compose(localCache.getUnchecked(j)).getMatrix();
                    }
                });
                    
                // triangles are stored as a one dimensional array of integers
                // every group of three, ex 0 1 2, are the corner of a triangle
                for(int i = 0 ; i < animMesh.triangles.size() - 2 ; i += 3) {
                    UnpackedBakedQuad.Builder quadBuilder = new UnpackedBakedQuad.Builder(format);
                    quadBuilder.setContractUVs(true);
                    Vector3f faceNormal = animMesh.getFaceNormal(i);
                    quadBuilder.setQuadOrientation(EnumFacing.getFacingFromVector(faceNormal.x, faceNormal.y, faceNormal.z));
                    
                    int i0 = animMesh.triangles.get(i);
                    int i1 = animMesh.triangles.get(i + 1);
                    int i2 = animMesh.triangles.get(i + 2);
                    WrappedVertex v0 = animMesh.vertices.get(i0);
                    WrappedVertex v1 = animMesh.vertices.get(i1);
                    WrappedVertex v2 = animMesh.vertices.get(i2);
                    
                    TextureAtlasSprite sprite = this.textures.get(animMesh.texture);
                    quadBuilder.setTexture(sprite);
                    putVertexData(quadBuilder, v0.pos, v0.norm, v0.texCoords, sprite);
                    putVertexData(quadBuilder, v1.pos, v1.norm, v1.texCoords, sprite);
                    putVertexData(quadBuilder, v2.pos, v2.norm, v2.texCoords, sprite);
                    putVertexData(quadBuilder, v2.pos, v2.norm, v2.texCoords, sprite);
                    builder.add(quadBuilder.build());
                }
            }
        }

        private final void putVertexData(UnpackedBakedQuad.Builder builder, Vector3f pos, Vector3f faceNormal, Vector2f texCoords, TextureAtlasSprite sprite)
        {
            // TODO handle everything not handled (texture transformations, bones, transformations, normals, e.t.c)

            for(int e = 0; e < format.getElementCount(); e++)
            {
                switch(format.getElement(e).getUsage())
                {
                    case POSITION:
                        builder.put(e, pos.x, pos.y, pos.z, 1);
                        break;
                    case COLOR:
                        builder.put(e, 1, 1, 1, 1);
                        break;
                    case UV:
                            builder.put(e,
                                    sprite.getInterpolatedU(texCoords.x * 16),
                                    sprite.getInterpolatedV(texCoords.y * 16),
                                    0,
                                    1
                            );
                        break;
                    case NORMAL:
                        builder.put(e, faceNormal.x, faceNormal.y, faceNormal.z, 0);
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
            return PerspectiveMapWrapper.handlePerspective(this, transforms, cameraTransformType);
        }

        @Override
        public ItemOverrideList getOverrides()
        {
            // TODO handle items
            return BakedMD5ModelOverrideHandler.INSTANCE;
        }
    }

    private static final class BakedMD5ModelOverrideHandler extends ItemOverrideList {
        public static final BakedMD5ModelOverrideHandler INSTANCE = new BakedMD5ModelOverrideHandler();
        private BakedMD5ModelOverrideHandler() {
            super(ImmutableList.of());
        }

        @Override
        public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity)
        {
            BakedWrapper model = (BakedWrapper) originalModel;
            if(model.state instanceof MD5State) {
                MD5State md5State = (MD5State) model.state;
                NBTTagCompound tag = stack.getTagCompound();
                if(tag != null) {
                    float partialTicks = Minecraft.getMinecraft().getRenderPartialTicks();
                    int frame = tag.getInteger("frame");
                    MD5State newState = new MD5State(md5State.animation, frame, frame + 1, partialTicks);
                    return new BakedWrapper(((BakedWrapper) originalModel).meshes, newState, true, true,
                            ((BakedWrapper) originalModel).format, ((BakedWrapper) originalModel).textures, ((BakedWrapper) originalModel).transforms);
                }
            }
            return originalModel;
        }
    }
}

