package com.flamingfrenchman.mcmd5.client.model;

import com.flamingfrenchman.mcmd5.Mcmd5;
import com.google.common.collect.*;

import javax.vecmath.*;
import java.io.*;
import net.minecraft.client.resources.IResourceManager;

import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Level;
import org.xml.sax.helpers.DefaultHandler;

public class MD5Model {
    // at some point, utilize IJoint and IModelState for animating
    // use Quat4f for interpolation. Look at other vector classes/
    // utilities to see if they have other useful functions

    private final ImmutableList<Mesh> meshes;
    private final ImmutableList<Joint> joints;

    public static boolean debugGeometry = false;
    public static boolean debugTextures = true;
    public static boolean debugNodes = true;

    public MD5Model(ImmutableList<Mesh> meshes, ImmutableList<Joint> joints) {
        this.meshes = meshes;
        this.joints = joints;
    }

    public ImmutableList<Mesh> getMeshes() { return this.meshes; }
    public ImmutableList<Joint> getJoints() { return this.joints; }
    
    public static void log(String str) {
        Mcmd5.logger.log(Level.INFO, str);
    }

    /*public ImmutableList<MD5Model.Triangle> bake(Function<MD5Model, Matrix4f> animator)
    {
        ImmutableList.Builder<MD5Model.Triangle> builder = ImmutableList.builder();

        for(MD5Model.Geometry geometry : geometries) {
            for(MD5Model.Triangle t : geometry.triangles.tris)
            {
                MD5Model.Vertex v1 = t.vertices[0].bake(this, animator);
                MD5Model.Vertex v2 = t.vertices[1].bake(this, animator);
                MD5Model.Vertex v3 = t.vertices[2].bake(this, animator);
                builder.add(new MD5Model.Triangle(v1, v2, v3));
            }
        }

        return builder.build();
    }*/

    public static class Parser extends DefaultHandler {
        private IResourceManager manager;
        private ResourceLocation location;
        private InputStream inputStream;
        BufferedReader bufferedReader;

        public Parser(InputStream in, IResourceManager manager, ResourceLocation location) throws IOException
        {
            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "New parser instance created");
            this.inputStream = in;
            this.manager = manager;
            this.location = location;
        }


        public MD5Model parse() throws IOException {
            //Joint[] joints = null;
            //Mesh[] meshes = null;
            ImmutableList.Builder<Mesh> meshBuilder = ImmutableList.builder();
            ImmutableList.Builder<Joint> jointBuilder = ImmutableList.builder();
            int numjoints = 0;
            int meshCount = 0;

            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "Parsing");
            try{
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                for(String line ; (line = bufferedReader.readLine()) != null; ) {
                    if(line.contains("numJoints")) {
                        //int len = Integer.parseInt(line.substring("numjoints".length()));
                        //joints = new Joint[len];
                    }
                    else if(line.contains("numMeshes")) {
                        //int len = Integer.parseInt(line.substring("numMeshes".length()));
                       // meshes = new Mesh[len];
                    }
                    if(line.contains("mesh")) {
                        meshBuilder.add(parseMesh());
                    }
                    else if(line.contains("joints")) {
                        jointBuilder.add(parseJoints(numjoints));
                    }
                }
                return new MD5Model(meshBuilder.build(), jointBuilder.build());
            }
            catch(Exception e) {
                throw e;
            }
            /*catch (ParserConfigurationException e) {
                if(Mcmd5.debug)
                    Mcmd5.logger.log(Level.INFO, "parsing failed; returning null");
            }*/
        }

        private Mesh parseMesh() {
            String shader = "";
            Vertex[] verts = null;
            Triangle[] tris = null;
            Weight[] weights = null;
            int vertcount = 0;
            int tricount = 0;
            int weightcount = 0;

            try {
                for(String line; !(line = bufferedReader.readLine()).contains("}"); ) {
                    if(line.contains("shader")) {
                        shader = line.trim().substring("shader \"".length(), line.length() - 1);
                    }
                    else if(line.contains("numverts")) {
                        int numverts = Integer.parseInt(line.trim().split(" ")[1]);
                        verts = new Vertex[numverts];
                    }
                    else if(line.contains("numtris")) {
                        int numtris = Integer.parseInt(line.trim().split(" ")[1]);
                        tris = new Triangle[numtris];
                    }
                    else if(line.contains("numweights")) {
                        int numweights = Integer.parseInt(line.trim().split(" ")[1]);
                        weights = new Weight[numweights];
                    }
                    else if(line.contains("tri")) {
                        String[] triData = line.trim().split(" ");
                        int v1 = Integer.parseInt(triData[2]);
                        int v2 = Integer.parseInt(triData[3]);
                        int v3 = Integer.parseInt(triData[4]);
                        tris[tricount++] = new Triangle(v1, v2, v3);
                    }
                    else if(line.contains("vert")) {
                        String[] vertData = line.trim().split(" ");
                        float u = Float.parseFloat(vertData[3]);
                        float v = Float.parseFloat(vertData[4]);
                        int weightstart = Integer.parseInt(vertData[6]);
                        int numweights = Integer.parseInt(vertData[7]);
                        verts[vertcount++] = new Vertex(new Vector2f(u, v), weightstart, numweights);
                    }
                    else if(line.contains("weight")) {
                        String[] weightData = line.trim().split(" ");
                        int jointIndex = Integer.parseInt(weightData[2]);
                        float bias = Float.parseFloat(weightData[3]);
                        float x = Float.parseFloat(weightData[5]);
                        float y = Float.parseFloat(weightData[6]);
                        float z = Float.parseFloat(weightData[7]);
                        weights[weightcount++] = new Weight(jointIndex, bias, new Vector3f(x, y, z));
                    }
                }
            }
            catch(IOException e) {

            }

            return new Mesh(shader, tris, verts, weights);
        }

        private Joint[] parseJoints(int numJoints) {
            Joint[] joints = new Joint[numJoints];
            int jointCount = 0;

            try {
                for(String line; !(line = bufferedReader.readLine()).contains("}"); jointCount++) {
                    String[] jointData = line.trim().split(" ");
                    int parent = Integer.parseInt(jointData[1]);
                    float x = Float.parseFloat(jointData[3]);
                    float y = Float.parseFloat(jointData[4]);
                    float z = Float.parseFloat(jointData[5]);
                    float qx = Float.parseFloat(jointData[8]);
                    float qy = Float.parseFloat(jointData[9]);
                    float qz = Float.parseFloat(jointData[10]);
                    //TODO: calculate quaternion w
                    joints[jointCount] = new Joint(jointData[0], parent,
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

    public static class Joint {
        private final String name;
        private final int parent;
        private final Vector3f pos;
        private final Quat4f rot;

        public Joint(String name, int parent, Vector3f pos, Quat4f rot) {
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

    public static class Mesh {
        private final String texture;
        private final Triangle[] triangles;
        private final Vertex[] vertices;
        private final Weight[] weights;

        // TODO: implement getNormal(Triangle/index)

        public Mesh(String texture, Triangle[] triangles, Vertex[] vertices, Weight[] weights) {
            this.texture = texture;
            this.triangles = triangles;
            this.vertices = vertices;
            this.weights = weights;
        }

        public String getTexture() { return this.texture; }
        public Triangle[] getTriangles() { return this.triangles; }
        public Vertex[] getVertices() { return this.vertices; }
        public Weight[] getWeights() {return this.weights; }

        /*public ImmutableList<Triangle> bake(Function<Node, Matrix4f> animator)
        {
            ImmutableList.Builder<Triangle> builder = ImmutableList.builder();
            for(Triangle t : triangles)
            {
                Vertex v1 = t.vertices[0].bake(this, animator);
                Vertex v2 = t.vertices[1].bake(this, animator);
                Vertex v3 = t.vertices[2].bake(this, animator);
                builder.add(new Triangle(v1, v2, v3, t.material));
            }
            return builder.build();
        }*/
    }

    public static class Triangle {
        private final int[] vertices;

        public Triangle(int v1, int v2, int v3) {
            this.vertices = new int[3];
            this.vertices[0] = v1;
            this.vertices[1] = v2;
            this.vertices[2] = v3;
        }

        public int getV1() { return vertices[0]; }
        public int getV2() { return vertices[1]; }
        public int getV3() { return vertices[2]; }
    }

    public static class Vertex {
        private final Vector2f texCoords;
        private final int weightStart;
        private final int numWeights;

        public Vector2f getTexCoords() { return this.texCoords; }
        public int getWeightStart() { return this.weightStart; }
        public int getNumweights() { return this.numWeights; }

        public Vertex(Vector2f texCoords, int weightStart, int numWeights) {
            this.texCoords = texCoords;
            this.weightStart = weightStart;
            this.numWeights = numWeights;
        }

        /*public Vertex bake(Mesh mesh, Function<Node, Matrix4f> animator)
        {
            // geometry
            Float totalWeight = 0f;
            Matrix4f t = new Matrix4f();
            // TODO: handle animations
            t.setIdentity();


            // pos
            Vector4f pos = new Vector4f(this.pos), newPos = new Vector4f();
            pos.w = 1;
            t.transform(pos, newPos);
            Vector3f rPos = new Vector3f(newPos.x / newPos.w, newPos.y / newPos.w, newPos.z / newPos.w);

            // normal
            Vector3f rNormal = null;

            if(this.normal != null)
            {
                Matrix3f tm = new Matrix3f();
                t.getRotationScale(tm);
                tm.invert();
                tm.transpose();
                Vector3f normal = new Vector3f(this.normal);
                rNormal = new Vector3f();
                tm.transform(normal, rNormal);
                rNormal.normalize();
            }

            // texCoords TODO
            return new Vertex(rPos, rNormal, color, texCoords);
        }*/
    }

    public static class Weight {
        private final int jointIndex;
        private final float bias;
        private final Vector3f pos;

        public Weight(int jointIndex, float bias, Vector3f pos) {
            this.jointIndex = jointIndex;
            this.bias = bias;
            this.pos = pos;
        }

        public int getJointIndex() { return this.jointIndex; }
        public float getBias() { return this.bias; }
        public Vector3f getPos() { return this.pos; }
    }
}
