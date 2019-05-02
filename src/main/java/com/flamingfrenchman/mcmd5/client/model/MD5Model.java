package com.flamingfrenchman.mcmd5.client.model;

import com.flamingfrenchman.mcmd5.Mcmd5;
import com.google.common.collect.*;

import javax.annotation.Nullable;
import javax.vecmath.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.ByteBuffer;
import java.rmi.server.ExportException;
import java.util.*;
import java.util.function.Function;

import com.sun.istack.internal.NotNull;
import net.minecraft.client.resources.IResourceManager;

import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import scala.xml.Elem;

public class MD5Model {
    // at some point, utilize IJoint and IModelState for animating
    // use Quat4f for interpolation. Look at other vector classes/
    // utilities to see if they have other useful functions

    private final ImmutableList<Mesh> meshes;
    private final ImmutableList<Joint> joints;
    private final ImmutableList<ResourceLocation> textures;

    public static boolean debugGeometry = false;
    public static boolean debugTextures = true;
    public static boolean debugNodes = true;
    
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
        private byte[] buffer;

        public Parser(InputStream in, IResourceManager manager, ResourceLocation location) throws IOException
        {
            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "New parser instance created");
            this.in = in;
            this.manager = manager;
            this.location = location;
        }


        public MD5Model parse() throws IOException {
            if(Mcmd5.debug)
                Mcmd5.logger.log(Level.INFO, "Parsing");
            try{
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                for(String line ; (line = bufferedReader.readLine()) != null; ) {

                }
                return  null;
            }
            catch(Exception e) {
                throw e;
            }
            /*catch (ParserConfigurationException e) {
                if(Mcmd5.debug)
                    Mcmd5.logger.log(Level.INFO, "parsing failed; returning null");
            }*/
        }

        public void interpret(String line) {
            
        }
    }

    public static class Joint {
        private final String name;
        private final int parent;
        private final Vector3f pos;
        private final Quat4f rot;


    }

    public static class Mesh {
        private final Triangle[] triangles;
        private final Vertex[] vertices;
        private final Weight[] weights;

        public Mesh(List<Triangle> triangles, List<Vertex> vertices, Map<String, Source> sources, List<Input> inputs) {
            this.triangles = triangles;
        }

        public ImmutableList<Triangle> bake(Function<Node, Matrix4f> animator)
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
        }
    }

    public static class Triangle {
        public final Vertex[] vertices;

        public Triangle(Vertex v1, Vertex v2, Vertex v3) {
            this.vertices = new Vertex[3];
            this.vertices[0] = v1;
            this.vertices[1] = v2;
            this.vertices[2] = v3;
        }

        public Triangle(Vertex[] vertices) {
            this.vertices = new Vertex[3];
            this.vertices[0] = vertices[0];
            this.vertices[1] = vertices[1];
            this.vertices[2] = vertices[2];
        }

        public static Vector3f getNormal(MD5Model.Vertex v1, MD5Model.Vertex v2, MD5Model.Vertex v3)
        {
            Vector3f a = new Vector3f(v2.pos);
            a.sub(v1.pos);
            Vector3f b = new Vector3f(v3.pos);
            b.sub(v1.pos);
            Vector3f c = new Vector3f();
            c.cross(a, b);
            c.normalize();
            return c;
        }

        public Vector3f getNormal() {
            Vector3f a = new Vector3f(getV2().pos);
            a.sub(getV1().pos);
            Vector3f b = new Vector3f(getV3().pos);
            b.sub(getV1().pos);
            Vector3f c = new Vector3f();
            c.cross(a, b);
            c.normalize();
            return c;
        }

        public Vertex getV1() { return vertices[0]; }
        public Vertex getV2() { return vertices[1]; }
        public Vertex getV3() { return vertices[2]; }
    }

    public static class Vertex {
        private final int index;
        private final Vector2f texCoords;
        private final int weightStart;
        private final int numweights;

        public int getIndex() { return this.index; }
        public Vector2f getTexCoords() { return this.texCoords; }
        private int getWeightStart() { return this.weightStart; }
        private int getNumweights() { return this.numweights; }

        public Vertex bake(Mesh mesh, Function<Node, Matrix4f> animator)
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
        }
    }

    public static class Weight {
        private final int index;
        private final int jointIndex;
        private final float bias;
        private final Vector3f pos;

        public int getIndex() { return this.index; }
        public int getJointIndex() { return this.jointIndex; }
        public float getBias() { return this.bias; }
        public Vector3f getPos() { return this.pos; }
    }
}
