Class Finder Tool Introduction
==============================

Java Class-Path tells Java where to look on the file system for files defining these classes. For generic java application, all class files are searched/loaded from the jar files, or class folders, based on these path item sequence. 

Usually, the developer would like to find class in one or many jar files, like the following situation,

- Know which class is loaded, if same class name hosted in different jar files;
- List all duplicated class names to eliminate incorrect class loading issue;
- Find class full name based on specified Simple class name or name with wild card character;
- List some class with specified constant string; 

The `classfinder` library/tool is helping Java developer to find class easily inside or outside JVM without source code provided. This manual is mainly to introduce the core function for this tool running as standalone application from command line. 

Basic Usage
-----------

### Run in Command-Line 

The classfinder support two ways by specifying jar files. 

- Add jar files as parameter `-classpath`

	`java -jar classfinder.jar -classpath file1.jar;file2.jar;file3.jar NameToSearch` 

- Use jar file list from Std in (or pipeline) 

	`dir /b path\to\folder\*.jar | java -jar classfinder.jar NameToSearch` 

	or more powerful command `find` in Unix

	`find /path/to/folder -name "*.jar" | java -jar classfinder.jar NameToSearch` 

To know which jar files are involved, use "-verbose" parameter to enable file list: 

    $ find /path/to/folder -name "*.jar" | java -jar classfinder.jar -verbose NameToSearch1 NameSearch2 
    ***** START CLASSPATH ***** 
    /path/to/folder/file1.jar 
    /path/to/folder/file2.jar 
    /path/to/folder/sub/file3.jar 
    ***** END CLASSPATH ***** 
    
    ***** START ARGUMENTS ***** 
    NameToSearch1 
    NameToSearch2 
    ***** END ARGUMENTS ***** 


#### Display Full Class Name and File Location 

The command can display full class name by specify simple class name, or with wild card character. The candidate full class name list, with the source file name will be shown. If same class find different jar files, the file name will be displayed by sequence of path order. 

    C:\> dir /b *.jar | java -jar classfinder.jar String 
    [com.sun.org.apache.xpath.internal.operations.String] 
    C:\Program Files (x86)\Java\jre6\lib\rt.jar 
    [java.lang.String] 
    C:\Program Files (x86)\Java\jre6\lib\rt.jar 

In addition, a switch flag `-group` can group result by jar file name. 
 
    C:\> dir /b *.jar | java -jar classfinder.jar -group Util* 
    -- From [C:\classfinder.jar] 
    com.hp.it.gadsc.et.ei.tools.classfinder.Util 
    ... 
    com.hp.it.gadsc.et.ei.tools.classfinder.Util$SufixFilter 
    -- From [C:\rt.jar] 
    com.sun.corba.se.impl.javax.rmi.CORBA.Util 
    com.sun.corba.se.impl.javax.rmi.CORBA.Util$1 
    ... 
    sun.rmi.server.Util 
    sun.text.normalizer.Utility 
     

#### Find Duplicate Classes 

To list duplicate class by its simple name, or wild card names, use `-duplicate` parameter with name parameter can list all duplicated class. The jar file name is listed by its path order, which means the effective one is in the first. 

    C:\>dir /b *.jar | java -jar classfinder.jar -duplicate Util* 
    [com.hp.it.gadsc.et.ei.tools.classfinder.Util] 
    C:\classfinder-dup.jar 
    C:\classfinder.jar 
    ... 
    [com.hp.it.gadsc.et.ei.tools.classfinder.Util$SufixFilter] 
    C:\classfinder-dup.jar 
    C:\classfinder.jar 


#### List Class under Package Name 

All class under the specified package (and its sub-packages) can be displayed over all jar files with `-package` parameter and name parameter. 
 
    C:\>dir /b *.jar | java -jar classfinder.jar -package java.lang 
    -- Class in package [java.lang] 
    java.lang.StackTraceElement 
    java.lang.ApplicationShutdownHooks$1 
    java.lang.StrictMath 
    ... 


### Class Relationship Usage 

#### List Super Class Chain (Up Hierarchy) 

To see all super class and interface names for the specified class, use `-super` parameter and full class name.

    C:\>dir /b *.jar | java -jar classfinder.jar -super java.util.Properties 
    -- Super class for [java.util.Properties] 
    java.util.Hashtable 
    java.util.Dictionary 
    java.lang.Object 
    java.io.Serializable 
    java.lang.Cloneable 
    java.util.Map 


#### List Sub-Classes (Down Hierarchy) 

To see all sub class or interface names for the specified class, use `-sub` parameter and full class name.

    C:\>dir /b *.jar | java -jar classfinder.jar -super java.util.Properties 
    -- Sub class for [java.util.Properties] 
    com.sun.security.sasl.Provider 
    java.security.AuthProvider 
    java.security.Provider 
    ... 
    sun.security.smartcardio.SunPCSC 


#### List Incoming Referring Classes (References) 

To get reference of the specified class over the jar files, use `-ref` parameter and full class name can provide all class which dpends on this class.

    C:\>dir /b *.jar | java -jar classfinder.jar -ref java.lang.Shutdown 
    -- Reference class for [java.lang.Shutdown] 
    java.lang.ApplicationShutdownHooks 
    java.lang.Runtime 
    java.lang.Shutdown 
    java.lang.Shutdown$1 
    java.lang.Shutdown$Lock 
    java.lang.System$2 
    java.lang.Terminator$1 


#### List Outgoing Depending Classes 

Also to know all depending classes over the jar files, use `-depend` parameter and full class name can provide all class which used by this class. That means if no such class in classpath, it may trigger class not found error.

The result shows existing dependence class name, and non-founded dependence class names (some may related to JRE). 

    C:\>dir /b *.jar | java -jar classfinder.jar -depend com.hp.it.gadsc.et.ei.tools.classfinder.Util 
    -- Dependence class for [com.hp.it.gadsc.et.ei.tools.classfinder.Util] 
    com.hp.it.gadsc.et.ei.tools.classfinder.AbstractClassFinder 
    com.hp.it.gadsc.et.ei.tools.classfinder.ClassFinder 
    com.hp.it.gadsc.et.ei.tools.classfinder.ClassLoaderFinder 
    ... 
    -- Non-founded dependence class for [com.hp.it.gadsc.et.ei.tools.classfinder.Util] 
    com.sun.org.apache.bcel.internal.classfile.Attribute 
    com.sun.org.apache.bcel.internal.classfile.ClassParser 
    ... 


#### List Incoming Referring Classes for Method (Caller List) 

To get reference of the specified method in the class, use `-mref` parameter and full method with class name can provide related class name list. 

    C:\>dir /b *.jar | java -jar classfinder.jar -mref java.lang.System.exit 
    -- Reference class for Method [java.lang.System.exit] 
    com.hp.it.gadsc.et.ei.tools.classfinder.ClassPathFinderMain 
    com.hp.it.gadsc.et.ei.tools.classfinder.JarCat 
    com.sun.corba.se.impl.activation.Quit 
    com.sun.corba.se.impl.activation.ServerMain 
    com.sun.corba.se.impl.naming.cosnaming.TransientNameServer 
    ... 


### Other Usage 

#### List Class containing String Text 

During some application running, if want to know where the log string is created, we can first use `-strings` to find possible class, then look into them (or source code) by using other tools. 

    C:\>dir /b *.jar | java -jar classfinder.jar -strings "Options:" 
    -- Class contains text "Options:" 
    [com.sun.servicetag.Installer] 
    Internal Options: 
    [sun.tools.jar.resources.jar] 
    Usage: jar {ctxui}[vfm0Me] [jar-file] [manifest-file] [entry-point] [-C dir] files ... 
    Options: 
    ... 

#### Display Text File Content 

If the jar file contains some property file, xml file, or java source file, use `-cat` parameter can help to view its text directly without extracting them. 
 
    C:\>dir /b *.jar | java -jar classfinder.jar -cat ClassFinder 
    ::[1] com/hp/it/gadsc/et/ei/tools/classfinder/ClassFinder.class 
    ::Warn: "com/hp/it/gadsc/et/ei/tools/classfinder/ClassFinder.class" is not a text file, may be content type of application/java-vm 
    ::[2] com/hp/it/gadsc/et/ei/tools/classfinder/ClassFinder.java 
    package com.hp.it.gadsc.et.ei.tools.classfinder; 
    
    import java.net.URL; 
    import java.util.List; 
    import java.util.Map; 
    
    public interface ClassFinder { 
    ... 

### Full Option 

Here are the full usage for reference. Type `java -jar classfinder.jar` or incorrect parameter can also print these information in console. 

    Usage: classfinder [SCOPE] [OPTION] NAME... 
    Find class name from classpath. 
    
    SCOPE: 
    -pid <PID> 
    Lookup class from the specified local process. (NOTE: Classpath longer than 1024 chars, may cause missing path item.) 
    -cmd <String in CMD> 
    Lookup class from the local process by string in command. (NOTE: Classpath longer than 1024 chars, may cause missing path item.) 
    -classpath <PATH LIST> [-jre <JAVA HOME>] 
    Lookup class from the provided class path 
    (EMPTY) 
    Find class from the class path loaded from std in. It accept path seperator or new line seperator 
    
    OPTION: 
    (EMPTY) 
    List class names and group by name 
    -group 
    Group class by its source 
    -package 
    List class names by package name, which is specified in NAME 
    -super 
    List all super class or interface names 
    -sub 
    List all children class or interface names 
    -duplicate 
    List all dupliated class or interface names 
    -ref 
    List all incoming referring class or interface names 
    -mref 
    List all incoming referring class or interface names for the full method name 
    -depend 
    List all outgoing depending class or interface names 
    -strings 
    List class or interface contains the string text 
    -cat 
    Print text file with name matching the string text 
    -verbose 
    List class path will be searched 
    -current 
    Add current java home into classpath if no explict java home specified 
    
    NAME: 
    java.lang.Object The full class name 
    'Object*' The class name with wildcards, but without package name. 
    This is only support for class name finding. 
    'java.util.*List' The full class name with wildcards(like '*', or '?'). This is only support for class name finding. 
    'java.util.Collection.toArray' The full method name. This is only support for method name based finding. 
 
