#!/usr/bin/env groovy

import groovy.io.FileType
// groovy実行環境にはあるが、groovy-all.jarにはない
import org.apache.commons.cli.Option

// optionalArgsはargsが1以上の場合にオプションの引数が省略ができるか
// http://mail-archives.apache.org/mod_mbox/groovy-users/201602.mbox/%3CB8D164BED956C5439875951895CB4B2250BE80D4@CAFRFD1MSGUSRJH.ITServices.sbc.com%3E
def cli = new CliBuilder(usage: "MvnDuplicateFileChecker.groovy")
cli.with {
    f(required: true, args: 1, argName: "pom.xml", "pom file")
    i(longOpt: "include", args: Option.UNLIMITED_VALUES, valueSeparator: ",", argName: "Include scopes", "default:compile,runtime")
    s(longOpt: "skipMaven", argName: "skipMaven", "skipMaven")
    t(longOpt: "tsv", "output tsv")
    h(longOpt: "help", argName: "help", "usage")
}

def options = cli.parse(args)
if (!options) {
    return;
}

if (options.h) {
    println cli.usage()
    return
}

def tsvMode = options.t;
def log = { text ->
    if (!tsvMode) {
        println text
    }
}

log "tsvMode: $tsvMode"


def pom = options.f
if (!pom) {
    pom = "pom.xml"
}
log "pom: $pom"

def includeScope = options.is
if (!includeScope) {
    includeScope = ["compile", "runtime"]
}
log "includeScope: $includeScope"

def skipMaven = options.s;
log "skipMaven: $skipMaven"




if (!skipMaven) {
    def mvn = "mvn -f $pom clean dependency:unpack-dependencies -Dmdep.useSubDirectoryPerArtifact=true -Dmdep.useSubDirectoryPerScope=true".execute()
    if (!tsvMode) {
        mvn.consumeProcessOutput((Appendable) System.out, (Appendable) System.err)
    }
    mvn.waitFor()
}

def projectDir = new File(pom).parent
log "projectDir: $projectDir"

def unpackDir = new File(projectDir, "target/dependency")
log "unpackDir: $unpackDir"

fileArtifactListMap = [:]
unpackDir.eachDir { scope ->
    def scopeName = scope.name
    if (!includeScope.contains(scopeName)) {
        return;
    }
    log "analyzing $scopeName scope"
    scope.eachDir { artifact ->
        def artifactName = artifact.name
        log "  analyzing $artifactName"
        artifact.traverse(
                type: FileType.FILES) { file ->
            def fileName = file.path - (artifact.path + "/")
            if (!fileArtifactListMap[fileName]) {
                fileArtifactListMap[fileName] = []
            }
            fileArtifactListMap[fileName] << artifactName
        }
    }
}

fileArtifactListMap.each { fileName, artifactList ->
    if (artifactList.size() >= 2) {
        if (tsvMode) {
            artifactList.each {
                println "$fileName\t$it"
            }
        } else {
            println fileName
            def last = artifactList.last()
            artifactList.each {
                if (it == last) {
                    print " └"
                } else {
                    print " ├"
                }
                println " $it"
            }
        }
    }
}
