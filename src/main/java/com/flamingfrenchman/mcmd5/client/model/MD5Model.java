package com.flamingfrenchman.mcmd5.client.model;

import com.flamingfrenchman.mcmd5.Mcmd5;
import com.google.common.collect.*;

import javax.vecmath.*;
import java.io.*;
import java.util.regex.Pattern;

import jdk.internal.util.xml.impl.Input;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.logging.log4j.Level;
import org.apache.commons.io.FileUtils;
import org.xml.sax.helpers.DefaultHandler;

public class MD5Model {
    // at some point, utilize IJoint and IModelState for animating
    // use Quat4f for interpolation. Look at other vector classes/
    // utilities to see if they have other useful functions

    private final ImmutableList<MD5Mesh> meshes;
    private final ImmutableList<MD5Joint> joints;
    private final ImmutableList<MD5Transform> transforms;

    public static boolean debugGeometry = false;
    public static boolean debugTextures = true;
    public static boolean debugNodes = true;

    public MD5Model(ImmutableList<MD5Mesh> meshes, ImmutableList<MD5Joint> joints, ImmutableList<MD5Transform> transforms) {
        this.meshes = meshes;
        this.joints = joints;
        this.transforms = transforms;
    }

    public ImmutableList<MD5Mesh> getMeshes() { return this.meshes; }
    public ImmutableList<MD5Joint> getJoints() { return this.joints; }
    public ImmutableList<MD5Transform> getTransforms() { return this.transforms; }
    
    public static void log(String str) {
        Mcmd5.logger.log(Level.INFO, str);
    }

    public static class Parser extends DefaultHandler {
        private IResourceManager manager;
        private InputStream inputStream;
        private InputStream animInputStream;
        private Pattern regex = Pattern.compile("\\s");
        private BufferedReader bufferedReader;
        private ResourceLocation location;
        private int numJoints;
        private int numTransforms;
        private int animJoints;
        private int meshCount;
        private int numFrames;
        private int frameRate;
        private int numAnimatedComponents;

        public Parser(IResource resource, IResourceManager manager, ResourceLocation file) throws IOException
        {
            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "New parser instance created");
            this.inputStream = resource.getInputStream();
            this.manager = manager;
            this.location = file;

            ResourceLocation animLocation = new ResourceLocation(file.getResourceDomain(),
                    file.getResourcePath().substring(0, ".md5mesh".length()) + ".md5anim");
            IResource animResourse = null;

            try {
                animResourse = manager.getResource(animLocation);
                animInputStream = animResourse.getInputStream();
            }
            catch (Exception e) {
                Mcmd5.logger.log(Level.INFO, "Could not find anim file for " + file.toString() + ", assuming none associated.");
            }
        }


        public MD5Model parse() throws IOException {
            ImmutableList.Builder<MD5Mesh> meshBuilder = ImmutableList.builder();
            ImmutableList.Builder<MD5Joint> jointBuilder = ImmutableList.builder();
            ImmutableList.Builder<MD5Transform> transformBuilder = ImmutableList.builder();
            int meshcount = 0;

            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "Parsing");
            try{
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                for(String line ; (line = bufferedReader.readLine()) != null; ) {
                    if(line.isEmpty() || line.trim().startsWith("//")) continue;
                    if(line.contains("//")) line = line.split("//")[0];
                    if(line.contains("MD5Version") || line.contains("commandline")) continue;

                    if(line.contains("numJoints")) {
                        numJoints = Integer.parseInt(line.substring("numJoints".length()).trim());
                    }
                    else if(line.contains("numMeshes")) {
                        meshCount = Integer.parseInt(line.substring("numMeshes".length()).trim());
                    }
                    else if(line.contains("numTransforms")) {
                        numTransforms = Integer.parseInt(line.substring("numTransforms".length()).trim());
                    }
                    else if(line.contains("mesh")) {
                        log("adding mesh number " + meshcount++);
                        MD5Mesh mesh = parseMesh();
                        meshBuilder.add(mesh);
                    }
                    else if(line.contains("joints")) {
                        MD5Joint[] joints = parseJoints(numJoints);
                        log("read joints: ");
                        for(MD5Joint joint : joints) {
                            log("\t" + joint.toString());
                        }
                        jointBuilder.add(joints);
                    }
                    else if(line.contains("transforms")) {
                        MD5Transform[] transforms = parseTransforms(numTransforms);
                        log("read transforms");
                        for(MD5Transform transform : transforms) {
                            log("\t" + transform.toString());
                        }
                        transformBuilder.add(transforms);
                    }
                }
            }
            finally {
                bufferedReader.close();
            }

            /*try {
                bufferedReader = new BufferedReader(new InputStreamReader(animInputStream));
                MD5AnimJoint[] hierarchy = null;
                MD5Loader.Key[][] keymat = new MD5Loader.Key[][];
                ImmutableTable.Builder<Integer, MD5Loader.WrappedJoint, MD5Loader.Key> keys = ImmutableTable.builder();
                int currentFrame = 0;


                for(String line ; (line = bufferedReader.readLine()) != null; ) {
                    if(line.contains("numFrames")) {
                        numFrames = Integer.parseInt(line.substring("numFrames".length()).trim());
                    }
                    else if(line.contains("numJoints")) {
                        numJoints = Integer.parseInt(line.substring("numJoints".length()).trim());
                    }
                    else if(line.contains("frameRate")) {
                        frameRate = Integer.parseInt(line.substring("frameRate".length()).trim());
                    }
                    else if(line.contains("numAnimatedComponents")) {
                        numAnimatedComponents = Integer.parseInt(line.substring("numAnimatedComponents".length()).trim());
                    }
                    else if(line.contains("hierarchy")) {
                        hierarchy = parseAnimJoints(numJoints);
                    }
                    else if(line.contains("bounds")) {
                        parseBounds();
                    }
                    else if(line.contains("baseframe")) {
                        if(hierarchy == null) throw new IOException("MD5 baseframes defined before heierarchy");
                        parseBaseFrame(hierarchy);
                    }
                    else if(line.contains("frame")) {
                        int frameNum = Integer.parseInt(line.substring("frame ".length()).trim());
                        parseFrame(frameNum, hierarchy, keys);
                    }
                }
            }
            finally {
                bufferedReader.close();
            }*/

            return new MD5Model(meshBuilder.build(), jointBuilder.build(), transformBuilder.build());
        }

        private void parseBounds() {
            String line;

            try {
                while (!(line = bufferedReader.readLine()).contains("}")) {
                    // discard for now
                }
            }
            catch (IOException e) {

            }
        }

        private MD5Mesh parseMesh() {
            String shader = "";
            MD5Vertex[] verts = null;
            MD5Triangle[] tris = null;
            MD5Weight[] weights = null;
            String line;

            try {
                while((line = bufferedReader.readLine()) != null) {
                    if(line.isEmpty()) continue;
                    if(line.contains("shader")) {
                        shader = line.trim().substring("shader \"".length(), line.length() - 2);
                        if(shader.endsWith(".png")) shader = shader.substring(0, shader.length() - ".png".length());
                    }
                    if(line.contains("numverts")) {
                        int numverts = Integer.parseInt(regex.split(line.trim())[1]);
                        log("\treading " + numverts + "vertices");
                        verts = new MD5Vertex[numverts];
                        while ((line = bufferedReader.readLine()).contains("vert")) {
                            if(line.trim().startsWith("//") || line.isEmpty()) continue;
                            if(line.contains("//")) line = line.split("//")[0];
                            String[] vertData = regex.split(line.trim());

                            int index = Integer.parseInt(vertData[1]);
                            float u = Float.parseFloat(vertData[3]);
                            float v = Float.parseFloat(vertData[4]);
                            int weightstart = Integer.parseInt(vertData[6]);
                            int numweights = Integer.parseInt(vertData[7]);
                            verts[index] = new MD5Vertex(new Vector2f(u, v), weightstart, numweights);
                        }
                    }
                    if(line.contains("numtris")) {
                        int numtris = Integer.parseInt(regex.split(line.trim())[1]);
                        log("\treading " + numtris + "triangles");
                        tris = new MD5Triangle[numtris];
                        while ((line = bufferedReader.readLine()).contains("tri")) {
                            if(line.trim().startsWith("//") || line.isEmpty()) continue;
                            if(line.contains("//")) line = line.split("//")[0];
                            String[] triData = regex.split(line.trim());

                            int index = Integer.parseInt(triData[1]);
                            int v1 = Integer.parseInt(triData[2]);
                            int v2 = Integer.parseInt(triData[3]);
                            int v3 = Integer.parseInt(triData[4]);
                            tris[index] = new MD5Triangle(v1, v2, v3);
                        }
                    }
                    if(line.contains("numweights")) {
                        int numweights = Integer.parseInt(regex.split(line.trim())[1]);
                        log("\treading " + numweights + "weights");
                        weights = new MD5Weight[numweights];
                        while ((line = bufferedReader.readLine()).contains("weight")) {
                            if(line.trim().startsWith("//") || line.isEmpty()) continue;
                            if(line.contains("//")) line = line.split("//")[0];
                            String[] weightData = regex.split(line.trim());

                            int index = Integer.parseInt(weightData[1]);
                            int jointIndex = Integer.parseInt(weightData[2]);
                            float bias = Float.parseFloat(weightData[3]);
                            float x = Float.parseFloat(weightData[5]);
                            float y = Float.parseFloat(weightData[6]);
                            float z = Float.parseFloat(weightData[7]);
                            weights[index] = new MD5Weight(jointIndex, bias, new Vector3f(x, y, z));
                        }
                    }
                    if(line.contains("}")) {
                        break;
                    }
                }
            }
            catch(IOException e) {
                log("error paring mesh for MD5Mesh");
                return null;
            }

            //log("MD5Mesh with shader " + shader + " parsed.");

            return new MD5Mesh(shader, tris, verts, weights);
        }

        private MD5Joint[] parseJoints(int numJoints) {
            MD5Joint[] joints = new MD5Joint[numJoints];
            int jointCount = 0;

            try {
                String line;
                while(!(line = bufferedReader.readLine()).contains("}")) {
                    if(line.trim().startsWith("//") || line.isEmpty()) continue;
                    if(line.contains("//")) line = line.split("//")[0];
                    String[] jointData = regex.split(line.trim());

                    int parent = Integer.parseInt(jointData[1]);
                    float x = Float.parseFloat(jointData[3]);
                    float y = Float.parseFloat(jointData[4]);
                    float z = Float.parseFloat(jointData[5]);
                    float qx = Float.parseFloat(jointData[8]);
                    float qy = Float.parseFloat(jointData[9]);
                    float qz = Float.parseFloat(jointData[10]);
                    joints[jointCount++] = new MD5Joint(jointData[0], parent,
                            new Vector3f(x, y, z), calculateQuaternion(qx, qy, qz));
                }
                if(jointCount != numJoints) {
                    Mcmd5.logger.log(Level.WARN, "Number of joints for model " + location.toString() + " had fewer joints than expected.");
                }
            }
            catch (IOException e) {
                log("error parsing joints for MD5Model");
                return null;
            }
            return joints;
        }

        private MD5Transform[] parseTransforms(int numTransforms) {
            MD5Transform[] transforms = new MD5Transform[numTransforms];
            int transformCount = 0;

            try {
                String line;
                while(!(line = bufferedReader.readLine()).contains("}")) {
                    if(line.startsWith("//")) continue;
                    if(line.contains("//")) line = line.split("//")[0];
                    String[] transformData = regex.split(line.trim());
                    log("current transform: " + line);
                    String name = transformData[0].substring(1, transformData[0].length() - 1);
                    Vector3f pos = new Vector3f(Float.parseFloat(transformData[2]), Float.parseFloat(transformData[3]),
                            Float.parseFloat(transformData[4]));
                    Vector3f rot = new Vector3f(Float.parseFloat(transformData[7]), Float.parseFloat(transformData[8]),
                            Float.parseFloat(transformData[9]));
                    Vector3f scale = new Vector3f(Float.parseFloat(transformData[12]), Float.parseFloat(transformData[13]),
                            Float.parseFloat(transformData[14]));

                    transforms[transformCount++] = new MD5Transform(name, pos, rot, scale);
                }
            }
            catch (IOException e) {

            }
            return transforms;
        }

        public static Quat4f calculateQuaternion(float x, float y, float z) {
            float w = 1.0f - (x * x) - (y * y) - (z * z);

            if (w < 0.0f) {
                w = 0.0f;
            }
            else {
                w = -(float) (Math.sqrt(w));
            }
            return new Quat4f(x, y, z, w);
        }

        public MD5AnimJoint[] parseAnimJoints(int numJoints) {
            MD5AnimJoint[] joints = new MD5AnimJoint[numJoints];
            int count = 0;
            String line;

            try {
                while(!(line = bufferedReader.readLine()).contains("}")) {
                    String[] tokens = regex.split(line.trim());
                    int parent = Integer.parseInt(tokens[1]);
                    byte flags = Byte.parseByte(tokens[2]);
                    int startIndex = Integer.parseInt(tokens[3]);
                    joints[count++] = new MD5AnimJoint(tokens[0], parent, flags, startIndex);

                }
            }
            catch (IOException e) {
                log("error parsing animated joints for MD5Model");
                return null;
            }

            return joints;
        }

        public MD5Loader.Key[] parseFrame(int frame, MD5AnimJoint[] joints) {
            String line;
            StringBuilder builder = new StringBuilder();
            MD5Loader.Key[] keys = new MD5Loader.Key[joints.length];

            try {
                while (!(line = bufferedReader.readLine()).contains("}")) {
                    builder.append(line);
                }
            }
            catch(IOException e) {
                return null;
            }

            String[] frameData = regex.split(builder.toString());

            for(int i = 0 ; i < keys.length ; ++i) {
                MD5AnimJoint joint = joints[i];
                byte flags = joint.flags;
                int startIndex = joint.startIndex;
                Vector3f position = new Vector3f(joint.pos);
                Quat4f orientation = new Quat4f(joint.rot);

                if ((flags & 1) > 0) {
                    position.x = Float.parseFloat(frameData[startIndex++]);
                }
                if ((flags & 2) > 0) {
                    position.y = Float.parseFloat(frameData[startIndex++]);
                }
                if ((flags & 4) > 0) {
                    position.z = Float.parseFloat(frameData[startIndex++]);
                }
                if ((flags & 8) > 0) {
                    orientation.x = Float.parseFloat(frameData[startIndex++]);
                }
                if ((flags & 16) > 0) {
                    orientation.y = Float.parseFloat(frameData[startIndex++]);
                }
                if ((flags & 32) > 0) {
                    orientation.z = Float.parseFloat(frameData[startIndex++]);
                }
                // Update Quaternion's w component
                orientation = calculateQuaternion(orientation.x, orientation.y, orientation.z);
                keys[i] = new MD5Loader.Key(position, null, orientation);
            }
            return keys;
        }

        public void parseBaseFrame(MD5AnimJoint[] joints) {
            int jointCount = 0;
            String line;
            MD5Loader.WrappedJoint[] wrappedJoints = new MD5Loader.WrappedJoint[joints.length];

            try {
                while(!(line = bufferedReader.readLine()).contains("}")) {
                    String[] tokens = regex.split(line.trim());
                    float vx = Float.parseFloat(tokens[1]);
                    float vy = Float.parseFloat(tokens[2]);
                    float vz = Float.parseFloat(tokens[3]);
                    float nx = Float.parseFloat(tokens[6]);
                    float ny = Float.parseFloat(tokens[7]);
                    float nz = Float.parseFloat(tokens[8]);
                    Vector3f pos = new Vector3f(vx, vy, vz);
                    Quat4f rot = calculateQuaternion(nx, ny, nz);
                    joints[jointCount].pos = pos;
                    joints[jointCount].rot = rot;
                    ++jointCount;
                }
            }
            catch(IOException e) {

            }
        }
    }

    public static class MD5AnimJoint {
        private String name;
        private int parent;
        private byte flags;
        private int startIndex;
        private Vector3f pos;
        private Quat4f rot;

        public MD5AnimJoint(String name, int parent, byte flags, int startIndex) {
            this.name = name;
            this.parent = parent;
            this.flags = flags;
            this.startIndex = startIndex;
        }
    }

    public static class MD5Joint {
        private final String name;
        private final int parent;
        private final Vector3f pos;
        private final Quat4f rot;

        public MD5Joint(String name, int parent, Vector3f pos, Quat4f rot) {
            this.name = name;
            this.parent = parent;
            this.pos = pos;
            this.rot = rot;
        }

        public Quat4f getRot() { return rot; }
        public Vector3f getPos() { return pos; }
        public int getParent() { return parent; }
        public String getName() { return name; }
        public String toString() {
            return "Joint info: " + name + " " + parent + " " + pos + " " + rot;
        }
    }

    public static class MD5Mesh {
        private final String texture;
        private final MD5Triangle[] triangles;
        private final MD5Vertex[] vertices;
        private final MD5Weight[] weights;

        // TODO: implement getNormal(Triangle/index)

        public MD5Mesh(String texture, MD5Triangle[] triangles, MD5Vertex[] vertices, MD5Weight[] weights) {
            this.texture = texture;
            this.triangles = triangles;
            this.vertices = vertices;
            this.weights = weights;
        }

        public String getTexture() { return this.texture; }
        public MD5Triangle[] getTriangles() { return this.triangles; }
        public MD5Vertex[] getVertices() { return this.vertices; }
        public MD5Weight[] getWeights() {return this.weights; }

        public String toString() {
            try{
                StringBuilder builder = new StringBuilder();
                builder.append("tex: " + texture + " ");
                for(int i = 0 ; i < vertices.length ; ++i) {
                    builder.append("\n\t" + vertices[i].toString());
                }
                builder.append("\n");
                for (int i = 0 ; i < weights.length ; ++i) {
                    builder.append("\n\t" + weights[i].toString());
                }
                return builder.toString();
            }
            catch (NullPointerException e) {
                return "oof";
            }
        }
    }

    public static class MD5Triangle {
        private final int[] vertices;

        public MD5Triangle(int v1, int v2, int v3) {
            this.vertices = new int[3];
            this.vertices[0] = v1;
            this.vertices[1] = v2;
            this.vertices[2] = v3;
        }

        public int getV0() { return vertices[0]; }
        public int getV1() { return vertices[1]; }
        public int getV2() { return vertices[2]; }
    }

    public static class MD5Vertex {
        private final Vector2f texCoords;
        private final int weightStart;
        private final int numWeights;
        private Vector3f pos = new Vector3f();
        private Vector3f norm = new Vector3f();

        public Vector2f getTexCoords() { return this.texCoords; }
        public int getWeightStart() { return this.weightStart; }
        public int getNumweights() { return this.numWeights; }

        public MD5Vertex(Vector2f texCoords, int weightStart, int numWeights) {
            this.texCoords = texCoords;
            this.weightStart = weightStart;
            this.numWeights = numWeights;
        }

        public Vector3f getPos() { return pos; }
        public void setPos(Vector3f pos) { this.pos = pos; }
        public void addToPos(Vector3f in) { this.pos.add(in); }
        public Vector3f getNorm() { return norm; }
        public void setNorm(Vector3f norm) { this.norm = norm; }
        public void addToNorm(Vector3f in) { this.norm.add(in); }
        public String toString() {
            return pos + " " + norm + " " + texCoords + " " + weightStart + " " + numWeights;
        }
    }

    public static class MD5Weight {
        private final int jointIndex;
        private final float bias;
        private final Vector3f pos;

        public MD5Weight(int jointIndex, float bias, Vector3f pos) {
            this.jointIndex = jointIndex;
            this.bias = bias;
            this.pos = pos;
        }

        public int getJointIndex() { return this.jointIndex; }
        public float getBias() { return this.bias; }
        public Vector3f getPos() { return this.pos; }
        public String toString() { return jointIndex + " " + bias + " " + pos; }
    }

    public static class MD5Transform {
        private final String name;
        private final Vector3f pos;
        private final Vector3f rot;
        private final Vector3f scale;

        public MD5Transform(String name, Vector3f pos, Vector3f rot, Vector3f scale) {
            this.name = name;
            this.pos = pos;
            this.rot = rot;
            this.scale = scale;
        }

        public TRSRTransformation getTRSRTransform() {
            return new TRSRTransformation(this.pos, TRSRTransformation.quatFromXYZDegrees(rot), this.scale, null);
        }

        public String getName() { return this.name; }

        public String toString() {
            return "MD5Transform: " + this.name + " " + pos.toString() + " "
                    + rot.toString() + " " + scale.toString();
        }
    }
}
