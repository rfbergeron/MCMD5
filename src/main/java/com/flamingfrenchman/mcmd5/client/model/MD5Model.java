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
import org.apache.logging.log4j.Level;
import org.apache.commons.io.FileUtils;
import org.xml.sax.helpers.DefaultHandler;

public class MD5Model {
    // at some point, utilize IJoint and IModelState for animating
    // use Quat4f for interpolation. Look at other vector classes/
    // utilities to see if they have other useful functions

    private final ImmutableList<MD5Mesh> meshes;
    private final ImmutableList<MD5Joint> joints;

    public static boolean debugGeometry = false;
    public static boolean debugTextures = true;
    public static boolean debugNodes = true;

    public MD5Model(ImmutableList<MD5Mesh> meshes, ImmutableList<MD5Joint> joints) {
        this.meshes = meshes;
        this.joints = joints;
    }

    public ImmutableList<MD5Mesh> getMeshes() { return this.meshes; }
    public ImmutableList<MD5Joint> getJoints() { return this.joints; }
    
    public static void log(String str) {
        Mcmd5.logger.log(Level.INFO, str);
    }

    public static class Parser extends DefaultHandler {
        private IResourceManager manager;
        private InputStream inputStream;
        private InputStream animInputStream;
        private Pattern regex = Pattern.compile("\\s");
        BufferedReader bufferedReader;

        public Parser(IResource resource, IResourceManager manager, ResourceLocation file) throws IOException
        {
            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "New parser instance created");
            this.inputStream = resource.getInputStream();
            this.manager = manager;

            ResourceLocation animLocation = new ResourceLocation(file.getResourceDomain(),
                    file.getResourceDomain().substring(0, ".md5mesh".length()) + ".md5anim");
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
            //Joint[] joints = null;
            //Mesh[] meshes = null;
            ImmutableList.Builder<MD5Mesh> meshBuilder = ImmutableList.builder();
            ImmutableList.Builder<MD5Joint> jointBuilder = ImmutableList.builder();
            int numJoints = 0;
            int meshCount = 0;
            int numFrames = 0;
            int frameRate = 0;
            int numAnimatedComponents = 0;

            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "Parsing");
            try{
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                for(String line ; (line = bufferedReader.readLine()) != null; ) {
                    if(line.contains("numJoints")) {
                        numJoints = Integer.parseInt(line.substring("numJoints".length()).trim());
                    }
                    else if(line.contains("numMeshes")) {
                        meshCount = Integer.parseInt(line.substring("numMeshes".length()).trim());
                    }
                    if(line.contains("mesh")) {
                        meshBuilder.add(parseMesh());
                    }
                    else if(line.contains("joints")) {
                        jointBuilder.add(parseJoints(numJoints));
                    }
                }

                bufferedReader.close();
                bufferedReader = new BufferedReader(new InputStreamReader(animInputStream));
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

                    }
                    else if(line.contains("bounds")) {

                    }
                    else if(line.contains("baseframe")) {

                    }
                    else if(line.contains("frame")) {

                    }
                }
            }
            finally {
                bufferedReader.close();
            }

            return new MD5Model(meshBuilder.build(), jointBuilder.build());
        }

        private MD5Mesh parseMesh() {
            String shader = "";
            MD5Vertex[] verts = null;
            MD5Triangle[] tris = null;
            MD5Weight[] weights = null;
            int vertcount = 0;
            int tricount = 0;
            int weightcount = 0;

            try {
                for(String line; !(line = bufferedReader.readLine()).contains("}"); ) {
                    if(line.contains("shader")) {
                        shader = line.trim().substring("shader \"".length(), line.length() - 1);
                    }
                    else if(line.contains("numverts")) {
                        int numverts = Integer.parseInt(regex.split(line.trim())[1]);
                        verts = new MD5Vertex[numverts];
                    }
                    else if(line.contains("numtris")) {
                        int numtris = Integer.parseInt(regex.split(line.trim())[1]);
                        tris = new MD5Triangle[numtris];
                    }
                    else if(line.contains("numweights")) {
                        int numweights = Integer.parseInt(regex.split(line.trim())[1]);
                        weights = new MD5Weight[numweights];
                    }
                    else if(line.contains("tri")) {
                        String[] triData = regex.split(line.trim());
                        int v1 = Integer.parseInt(triData[2]);
                        int v2 = Integer.parseInt(triData[3]);
                        int v3 = Integer.parseInt(triData[4]);
                        tris[tricount++] = new MD5Triangle(v1, v2, v3);
                    }
                    else if(line.contains("vert")) {
                        String[] vertData = regex.split(line.trim());
                        float u = Float.parseFloat(vertData[3]);
                        float v = Float.parseFloat(vertData[4]);
                        int weightstart = Integer.parseInt(vertData[6]);
                        int numweights = Integer.parseInt(vertData[7]);
                        verts[vertcount++] = new MD5Vertex(new Vector2f(u, v), weightstart, numweights);
                    }
                    else if(line.contains("weight")) {
                        String[] weightData = regex.split(line.trim());
                        int jointIndex = Integer.parseInt(weightData[2]);
                        float bias = Float.parseFloat(weightData[3]);
                        float x = Float.parseFloat(weightData[5]);
                        float y = Float.parseFloat(weightData[6]);
                        float z = Float.parseFloat(weightData[7]);
                        weights[weightcount++] = new MD5Weight(jointIndex, bias, new Vector3f(x, y, z));
                    }
                }
            }
            catch(IOException e) {

            }

            return new MD5Mesh(shader, tris, verts, weights);
        }

        private MD5Joint[] parseJoints(int numJoints) {
            MD5Joint[] joints = new MD5Joint[numJoints];
            int jointCount = 0;

            try {
                for(String line; !(line = bufferedReader.readLine()).contains("}"); jointCount++) {
                    String[] jointData = regex.split(line.trim());
                    int parent = Integer.parseInt(jointData[1]);
                    float x = Float.parseFloat(jointData[3]);
                    float y = Float.parseFloat(jointData[4]);
                    float z = Float.parseFloat(jointData[5]);
                    float qx = Float.parseFloat(jointData[8]);
                    float qy = Float.parseFloat(jointData[9]);
                    float qz = Float.parseFloat(jointData[10]);
                    //TODO: calculate quaternion w
                    joints[jointCount] = new MD5Joint(jointData[0], parent,
                            new Vector3f(x, y, z), calculateQuaternion(qx, qy, qz));
                }
            }
            catch (IOException e) {

            }
            return joints;
        }

        public static Quat4f calculateQuaternion(float x, float y, float z) {
            Quat4f orientation = new Quat4f(x, y, z, 0);
            float temp = 1.0f - (orientation.x * orientation.x) - (orientation.y * orientation.y) - (orientation.z * orientation.z);

            if (temp < 0.0f) {
                orientation.w = 0.0f;
            }
            else {
                orientation.w = -(float) (Math.sqrt(temp));
            }
            return orientation;
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
        private Vector3f norm;

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
        public void setNorm() { this.norm = norm; }
        public void addToNorm(Vector3f in) { this.norm.add(in); }
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
    }
}
