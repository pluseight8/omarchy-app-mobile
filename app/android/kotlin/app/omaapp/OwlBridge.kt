package app.omaapp

import dev.omalauncher.widgets.lang.Bool
import dev.omalauncher.widgets.lang.Budget
import dev.omalauncher.widgets.lang.Col
import dev.omalauncher.widgets.lang.Compiler
import dev.omalauncher.widgets.lang.Env
import dev.omalauncher.widgets.lang.Ev
import dev.omalauncher.widgets.lang.Global
import dev.omalauncher.widgets.lang.Gradient
import dev.omalauncher.widgets.lang.Host
import dev.omalauncher.widgets.lang.Lst
import dev.omalauncher.widgets.lang.Null
import dev.omalauncher.widgets.lang.Num
import dev.omalauncher.widgets.lang.Obj
import dev.omalauncher.widgets.lang.OwlKind
import dev.omalauncher.widgets.lang.Program
import dev.omalauncher.widgets.lang.Source
import dev.omalauncher.widgets.lang.Str
import dev.omalauncher.widgets.lang.Value
import dev.omalauncher.widgets.lang.WidgetError
import dev.omalauncher.widgets.lang.WNode
import dev.omalauncher.widgets.lang.parseHex
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Android JNI boundary for the portable OWL compiler/runtime. The host deliberately exposes no
 * command, network, filesystem, media, or desktop-control implementation: home widgets are local
 * presentation and remain usable when the Omarchy computer is offline.
 */
object OwlBridge {
    private const val MAX_SOURCE_CHARS = 64 * 1024
    private const val MAX_RENDERED_NODES = 500
    private const val MAX_RENDER_DEPTH = 32
    private val supportedProperties = mapOf(
        "Column" to setOf("gap", "fill", "width", "height", "weight", "padding"),
        "Row" to setOf("gap", "fill", "width", "height", "weight", "padding"),
        "Box" to setOf("align", "width", "height", "fill", "weight", "padding"),
        "Text" to setOf("text", "color", "style", "font", "fontSize", "fontWeight", "bold", "italic", "letterSpacing", "align", "maxLines", "width", "height"),
        "Button" to setOf("text", "width", "height"),
        "Progress" to setOf("value", "max", "color", "track", "width", "height"),
        "Canvas" to setOf("width", "height"),
    )
    private val supportedDrawOps = setOf("rect", "circle", "arc", "line", "text", "path", "sprite", "translate", "rotate", "scale", "clip", "layer")

    private val startedAt = System.nanoTime()

    @JvmStatic
    fun render(source: String, width: Double, height: Double, themeJson: String): String {
        return try {
            if (source.length > MAX_SOURCE_CHARS) throw WidgetError("widget source exceeds 64 KB")
            val program = Compiler.compile(source)
            require(program.kind == OwlKind.Widget) { "Home store accepts widget files only" }
            require(!program.needsLinux && !program.terminal && program.requires.isEmpty()) {
                "this widget needs a Linux guest or package and cannot run offline"
            }
            require(program.commands.isEmpty()) { "this offline widget viewer does not run shell commands" }
            require(program.globals.filterIsInstance<Global.Source>().all { it.kind == "clock" }) {
                "this offline widget viewer supports the local clock source only"
            }
            require(program.triggers.isEmpty()) { "this offline widget viewer does not run background triggers" }
            require(program.onTap == null && program.onSubmit == null && program.onKey == null
                && program.onBack == null && program.screen == null) {
                "this offline widget viewer does not run OWL interaction handlers or screens"
            }
            require(program.globals.none { it is Global.State && it.secret }) {
                "secret state is only available to a networked widget runtime"
            }
            require(program.globals.none { it is Global.Asset || it is Global.Sampled }) {
                "offline widgets cannot use OWL assets or sampled state yet"
            }
            require(program.root.size <= MAX_RENDERED_NODES) { "widget tree exceeds the offline rendering limit" }

            val theme = runCatching { JSONObject(themeJson) }.getOrDefault(JSONObject())
            val host = OfflineHost(program, width, height, theme)
            val env = Env.root(host)
            val nodes = JSONArray()
            appendNodes(program.root, env, host, nodes, 0)
            val background = program.background?.let { host.evaluate(it, env) }
            require(background !is Gradient) {
                "gradient widget backgrounds are not supported by the offline surface renderer"
            }

            JSONObject()
                .put("name", program.name)
                .put("padding", program.padding)
                .put("size", program.size?.let { JSONArray().put(it.first).put(it.second) } ?: JSONObject.NULL)
                .put("background", background?.let { toJson(it) } ?: JSONObject.NULL)
                .put("animated", program.animated || program.globals.any { it is Global.Source })
                .put("nodes", nodes)
                .toString()
        } catch (error: dev.omalauncher.widgets.lang.CompileError) {
            errorJson(Source(source).format(error))
        } catch (error: StackOverflowError) {
            errorJson("widget is nested too deeply")
        } catch (error: Exception) {
            errorJson(error.message ?: "widget could not be rendered")
        }
    }

    private fun errorJson(message: String) = JSONObject().put("error", message).toString()

    private fun appendNodes(
        items: List<WNode>,
        env: Env,
        host: OfflineHost,
        target: JSONArray,
        depth: Int,
    ) {
        if (depth > MAX_RENDER_DEPTH) throw WidgetError("widget tree is nested too deeply")
        for (item in items) {
            host.stepNode()
            when (item) {
                is WNode.Element -> {
                    val properties = supportedProperties[item.kind]
                        ?: throw WidgetError("offline widgets do not support the ${item.kind} node")
                    require(item.handlers.isEmpty()) { "offline widgets cannot run tap handlers" }
                    require(item.animations.isEmpty()) { "offline widgets cannot run OWL node animations yet" }
                    require(item.props.keys.all { it in properties }) {
                        "offline ${item.kind} nodes contain a property the renderer does not support"
                    }
                    val props = JSONObject()
                    for ((name, expression) in item.props) {
                        val value = host.evaluate(expression, env)
                        require(value !is Gradient) {
                            "offline gradients are supported only on OWL canvas fill and stroke paints"
                        }
                        props.put(name, toJson(value))
                    }
                    val children = JSONArray()
                    val childEnv = if (item.kind == "Canvas") {
                        fun dimension(name: String, fallback: Double): Double =
                            (props.opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }
                                ?: fallback
                        env.child(Num(dimension("width", width)), Num(dimension("height", height)))
                    } else {
                        env
                    }
                    appendNodes(item.children, childEnv, host, children, depth + 1)
                    target.put(
                        JSONObject()
                            .put("type", item.kind)
                            .put("props", props)
                            .put("children", children)
                            .put("interactive", item.handlers.isNotEmpty()),
                    )
                }
                is WNode.If -> {
                    val branch = if (host.evaluate(item.cond, env).truthy) item.then else item.orElse
                    appendNodes(branch, env, host, target, depth + 1)
                }
                is WNode.For -> {
                    val values = host.evaluate(item.list, env) as? Lst ?: Lst(emptyList())
                    if (values.items.size + host.renderedNodes > MAX_RENDERED_NODES) {
                        throw WidgetError("widget expands to more than $MAX_RENDERED_NODES nodes")
                    }
                    values.items.forEachIndexed { index, value ->
                        host.budget.tick()
                        appendNodes(item.body, env.child(value, Num(index.toDouble())), host, target, depth + 1)
                    }
                }
                is WNode.Lets -> appendNodes(item.children, env.lets(item.values), host, target, depth + 1)
                is WNode.Use -> {
                    val componentEnv = env.child(*item.args.map { host.evaluate(it, env) }.toTypedArray())
                    val props = JSONObject()
                    for ((name, expression) in item.wrapper) {
                        val value = host.evaluate(expression, env)
                        require(value !is Gradient) {
                            "offline component wrappers cannot use gradient properties"
                        }
                        props.put(name, toJson(value))
                    }
                    val children = JSONArray()
                    appendNodes(item.body, componentEnv, host, children, depth + 1)
                    target.put(JSONObject().put("type", "Box").put("props", props).put("children", children))
                }
                is WNode.Draw -> {
                    require(item.op in supportedDrawOps) { "offline canvas does not support ${item.op}" }
                    target.put(drawJson(item, env, host, depth))
                }
            }
        }
    }

    private fun drawJson(item: WNode.Draw, env: Env, host: OfflineHost, depth: Int): JSONObject {
        val args = JSONArray()
        item.args.forEach { args.put(toJson(host.evaluate(it, env))) }
        val named = JSONObject()
        for ((name, expression) in item.named) {
            val value = host.evaluate(expression, env)
            require(value !is Gradient || name == "fill" || name == "stroke") {
                "offline gradients are supported only on OWL canvas fill and stroke paints"
            }
            named.put(name, toJson(value))
        }
        val children = item.children?.let {
            JSONArray().also { output -> appendNodes(it, env, host, output, depth + 1) }
        }
        val result = JSONObject()
            .put("type", "draw")
            .put("op", item.op)
            .put("args", args)
            .put("named", named)
        if (children != null) result.put("children", children)
        return result
    }

    private fun toJson(value: Value): Any = when (value) {
        Null -> JSONObject.NULL
        is Bool -> value.v
        is Num -> if (value.v.isFinite()) value.v else JSONObject.NULL
        is Str -> value.v
        is Col -> JSONObject()
            .put("__owlColor", true)
            .put("theme", value.theme ?: JSONObject.NULL)
            .put("argb", String.format("#%08x", value.argb))
            .put("alpha", value.alpha.toDouble())
        is Lst -> JSONArray().also { result -> value.items.forEach { result.put(toJson(it)) } }
        is Obj -> JSONObject().also { result -> value.fields.forEach { (key, item) -> result.put(key, toJson(item)) } }
        is Gradient -> JSONObject()
            .put("__owlGradient", true)
            .put("radial", value.radial)
            .put("coords", JSONArray(value.coords))
            .put("colors", JSONArray().also { result -> value.colors.forEach { result.put(toJson(it)) } })
        else -> JSONObject.NULL
    }

    private class OfflineHost(
        private val program: Program,
        private val width: Double,
        private val height: Double,
        private val theme: JSONObject,
    ) : Host {
        override val budget = Budget(200_000)
        private val slots = MutableList<Value?>(program.globals.size) { null }
        private val initializing = HashSet<Int>()
        var renderedNodes = 0
            private set

        fun stepNode() {
            budget.tick()
            if (++renderedNodes > MAX_RENDERED_NODES) {
                throw WidgetError("widget expands to more than $MAX_RENDERED_NODES nodes")
            }
        }

        fun evaluate(expression: Ev, env: Env) = budget.eval(expression, env)

        override fun read(slot: Int): Value {
            if (slot !in program.globals.indices) return Null
            slots[slot]?.let { return it }
            if (!initializing.add(slot)) throw WidgetError("widget state contains a cycle")
            val env = Env.root(this)
            val value = try {
                when (val global = program.globals[slot]) {
                    is Global.State -> evaluate(global.init, env)
                    is Global.Let -> evaluate(global.value, env)
                    is Global.Sampled -> if (global.prev) Null else evaluate(global.of, env)
                    is Global.Asset -> Str(Base64.getEncoder().encodeToString(global.bytes))
                    is Global.Source -> if (global.kind == "clock") {
                        Num(System.currentTimeMillis() / 1000.0)
                    } else {
                        Null
                    }
                }
            } finally {
                initializing.remove(slot)
            }
            slots[slot] = value
            return value
        }

        override fun write(slot: Int, value: Value) {
            if (slot in slots.indices) slots[slot] = value
        }

        override fun theme(name: String): Int? = theme.optString(name).takeIf { it.isNotEmpty() }?.let(::parseHex)

        override fun time(): Double = (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000_000.0

        override fun tile(name: String): Value = when (name) {
            "tileWidth", "screenWidth" -> Num(width)
            "tileHeight", "screenHeight" -> Num(height)
            else -> Null
        }

        override fun act(name: String, args: List<Value>) = Unit

        override fun runInto(command: String, slot: Int) = Unit

        override fun shortcutsInto(key: String, slot: Int) = Unit

        override fun post(url: String, body: Value, headers: Map<String, String>, slot: Int?, method: String) = Unit
    }
}
