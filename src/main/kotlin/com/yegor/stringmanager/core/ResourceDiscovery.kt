package com.yegor.stringmanager.core

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.yegor.stringmanager.core.model.LocaleSpec
import com.yegor.stringmanager.core.model.ResourceOrigin
import com.yegor.stringmanager.core.model.StringFile

/**
 * Finds all files with string resources of a project.
 *
 * Discovery is intentionally independent from the Android plugin: Android `res` directories and
 * Compose Multiplatform `composeResources` directories are both plain directories inside a module,
 * and both are matched by the same rules.
 */
class ResourceDiscovery(private val project: Project) {

    /**
     * Scans all modules of the project.
     *
     * @return files with string resources, grouped by module and directory
     */
    fun discover(): List<StringFile> {
        val result = mutableListOf<StringFile>()
        for (module in ModuleManager.getInstance(project).modules) {
            result += discover(module)
        }
        return result
    }

    /**
     * Scans a single module.
     */
    fun discover(module: Module): List<StringFile> {
        val result = mutableListOf<StringFile>()
        val roots = LinkedHashSet<VirtualFile>()
        roots += ModuleRootManager.getInstance(module).contentRoots
        roots += ModuleRootManager.getInstance(module).sourceRoots
        for (root in roots) {
            scanDirectory(root, module, result, 0)
        }
        return result
    }

    private fun scanDirectory(
        dir: VirtualFile,
        module: Module,
        result: MutableList<StringFile>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) {
            return
        }
        for (child in dir.children) {
            if (!child.isDirectory || child.name in SKIPPED_DIRECTORIES) {
                continue
            }
            when (child.name) {
                // `src/<sourceSet>/res` holds Android resources, `src/<sourceSet>/composeResources` holds Compose ones.
                RES_DIRECTORY -> result += collectValues(child, module, ResourceOrigin.ANDROID_RES)
                COMPOSE_RESOURCES_DIRECTORY -> result += collectValues(child, module, ResourceOrigin.COMPOSE_RESOURCES)
                else -> scanDirectory(child, module, result, depth + 1)
            }
        }
    }

    private fun collectValues(
        parent: VirtualFile,
        module: Module,
        origin: ResourceOrigin,
    ): List<StringFile> {
        val result = mutableListOf<StringFile>()
        for (directory in parent.children) {
            if (!directory.isDirectory || !directory.name.startsWith(VALUES_PREFIX)) {
                continue
            }
            val locale = LocaleQualifiers.parse(directory.name)
            for (file in directory.children) {
                if (file.isDirectory || !file.name.endsWith(XML_EXTENSION)) {
                    continue
                }
                result += StringFile(
                    module = module.name,
                    path = relativePath(file, module),
                    origin = origin,
                    locale = locale,
                )
            }
        }
        return result
    }

    /**
     * Returns the path of a file relative to the root of the project.
     *
     * The root is the common ancestor of the content roots of all modules, because the project
     * directory is not always available in the API.
     */
    private fun relativePath(file: VirtualFile, module: Module): String {
        val root = projectRoot()
        if (root != null) {
            val relative = VfsUtilCore.getRelativePath(file, root)
            if (relative != null && !relative.startsWith("/")) {
                return relative
            }
        }
        for (contentRoot in ModuleRootManager.getInstance(module).contentRoots) {
            val relative = VfsUtilCore.getRelativePath(file, contentRoot)
            if (relative != null && !relative.startsWith("/")) {
                return relative
            }
        }
        return file.path
    }

    /**
     * Returns the common ancestor of the content roots of all modules.
     */
    private fun projectRoot(): VirtualFile? {
        val roots = ModuleManager.getInstance(project).modules
            .flatMap { ModuleRootManager.getInstance(it).contentRoots.toList() }
        var root: VirtualFile? = roots.firstOrNull()
        for (candidate in roots.drop(1)) {
            root = commonAncestor(root, candidate)
        }
        return root
    }

    private fun commonAncestor(first: VirtualFile?, second: VirtualFile): VirtualFile? {
        var current = first
        while (current != null && !isAncestor(current, second)) {
            current = current.parent
        }
        return current
    }

    private fun isAncestor(ancestor: VirtualFile, file: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            if (current == ancestor) {
                return true
            }
            current = current.parent
        }
        return false
    }

    companion object {
        private const val VALUES_PREFIX = "values"
        private const val XML_EXTENSION = ".xml"
        private const val RES_DIRECTORY = "res"
        private const val COMPOSE_RESOURCES_DIRECTORY = "composeResources"
        private const val MAX_DEPTH = 8

        private val SKIPPED_DIRECTORIES = setOf(
            "build",
            "out",
            "target",
            "node_modules",
            ".git",
            ".gradle",
            ".idea",
            "captures",
            ".cxx",
            "intermediates",
        )
    }
}
