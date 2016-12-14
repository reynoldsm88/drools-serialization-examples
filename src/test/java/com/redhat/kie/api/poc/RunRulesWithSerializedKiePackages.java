package com.redhat.kie.api.poc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

import org.drools.core.common.DroolsObjectInputStream;
import org.drools.core.common.DroolsObjectOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.io.ResourceType;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.internal.io.ResourceFactory;

public class RunRulesWithSerializedKiePackages {

    private static final String SRC_MAIN_RESOURCES = System.getProperty( "user.dir" ) + File.separator + "src" + File.separator + "main" + File.separator + "resources";
    private static final String TARGET = System.getProperty( "user.dir" ) + File.separator + "target";
    private static final String BIN_FILE = "knowledge-packages.bin";

    @Before
    public void setup() throws IOException {
        System.err.println( "deleting bin file" );
        Files.deleteIfExists( Paths.get( TARGET + File.separator + BIN_FILE ) );
    }

    /**
     * Why doesn't the kbase contain the declared fact type?
     */
    @Test
    public void serializePackagesFromOneFileFailsWithClassNotFoundException() throws Exception {
        serializeOneFileAsBin( SRC_MAIN_RESOURCES + File.separator + "com.redhat.rules.one.file" + File.separator + "DebugRules.drl" );
        try {
            Collection<KnowledgePackage> packages = deserializeBinaryFile();
            KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
            kbase.addKnowledgePackages( packages );
        }
        catch ( Exception e ) {
            // when deserializing the packages it cannot find the declared class
            assertTrue( e.getCause() instanceof ClassNotFoundException );
            e.getMessage().contains( "TransientFact" );
        }
    }

    /**
     * Why does the kbase contain the declared fact type in this instance?
     */
    @Test
    public void serializePackagesWithOnlyDeclaredFactTypeContainsDeclaredFactType() throws Exception {
        serializeOneFileAsBin( SRC_MAIN_RESOURCES + File.separator + "com.redhat.rules.two.files" + File.separator + "DeclaredFact.drl" );
        Collection<KnowledgePackage> packages = deserializeBinaryFile();
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( packages );
        // kbase has the declared fact in it
        assertTrue( kbase.getFactType( "com.redhat.rules", "TransientFact" ) != null );
    }

    /**
     * Why doesn't the kbase contain the declared fact type?
     */
    @Test
    public void serializePackagesFromTwoFilesDoesNotContainDeclaredFactType() throws Exception {
        serializeTwoFilesAsBin();
        Collection<KnowledgePackage> packages = deserializeBinaryFile();
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( packages );
        assertFalse( kbase.getFactType( "com.redhat.rules", "TransientFact" ) != null );
    }

    @SuppressWarnings( "unchecked" )
    private Collection<KnowledgePackage> deserializeBinaryFile() throws Exception {
        FileInputStream fis = new FileInputStream( Paths.get( TARGET + File.separator + BIN_FILE ).toFile() );
        DroolsObjectInputStream in = new DroolsObjectInputStream( fis );
        Collection<KnowledgePackage> packages = (Collection<KnowledgePackage>) in.readObject();
        in.close();
        return packages;
    }

    private void serializeOneFileAsBin( String filename ) {
        KnowledgeBuilder builder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        try {
            builder.add( ResourceFactory.newFileResource( new File( filename ) ), ResourceType.DRL );
            FileOutputStream fos = new FileOutputStream( TARGET + File.separator + BIN_FILE );
            DroolsObjectOutputStream out = new DroolsObjectOutputStream( fos );
            out.writeObject( builder.getKnowledgePackages() );
            out.close();
        }
        catch ( Exception e ) {
            e.printStackTrace();
            fail( "encountered exception loading files" );
        }
    }

    private void serializeTwoFilesAsBin() {
        KnowledgeBuilder builder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        try {
            Files.list( Paths.get( SRC_MAIN_RESOURCES + File.separator + "com.redhat.rules.two.files" ) ).forEach( path -> {
                System.err.println( path.toFile().getAbsolutePath() );
                builder.add( ResourceFactory.newFileResource( path.toFile() ), ResourceType.DRL );
            } );
            FileOutputStream fos = new FileOutputStream( TARGET + File.separator + "knowledge-packages.bin" );
            DroolsObjectOutputStream out = new DroolsObjectOutputStream( fos );
            out.writeObject( builder.getKnowledgePackages() );
            out.close();
        }
        catch ( Exception e ) {
            e.printStackTrace();
            fail( "encountered exception loading files" );
        }
    }
}