import * as React from "react"
import { useEditor, EditorContent, useEditorState, type Editor } from "@tiptap/react"
import StarterKit from "@tiptap/starter-kit"

// Imperative handle exposed to callers (e.g. the merge-token bar), so they can
// splice content at the caret without owning the editor instance.
export type RichTextEditorHandle = {
  insertToken: (text: string) => void
  focus: () => void
}

type RichTextEditorProps = {
  value?: string
  onChange?: (html: string) => void
  placeholder?: string
  className?: string
}

// TipTap's schema wraps list-item content in a paragraph (`<li><p>x</p></li>`).
// Email clients apply default paragraph margins there, which renders as large
// gaps between bullets, so unwrap the single-paragraph case on output. Only the
// simple case is unwrapped — an <li> holding a nested list keeps its structure.
function cleanHTML(html: string): string {
  if (typeof window === "undefined" || !html) return html
  const doc = new DOMParser().parseFromString(`<body>${html}</body>`, "text/html")
  doc.body.querySelectorAll("li").forEach((li) => {
    const only = li.children.length === 1 ? li.firstElementChild : null
    if (only && only.tagName === "P") {
      while (only.firstChild) li.insertBefore(only.firstChild, only)
      only.remove()
    }
  })
  return doc.body.innerHTML
}

// Small toolbar button — reflects active state and toggles a mark/node.
function ToolbarButton({
  onClick,
  active,
  disabled,
  title,
  children,
}: {
  onClick: () => void
  active?: boolean
  disabled?: boolean
  title: string
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      aria-pressed={!!active}
      disabled={disabled}
      onMouseDown={(e) => e.preventDefault()}
      onClick={onClick}
      className={
        "inline-flex h-7 min-w-7 items-center justify-center rounded px-1.5 text-sm " +
        "transition-colors disabled:opacity-40 " +
        (active
          ? "bg-gray-950/10 text-foreground"
          : "text-muted-foreground hover:bg-gray-950/5 hover:text-foreground")
      }
    >
      {children}
    </button>
  )
}

function Toolbar({ editor }: { editor: Editor }) {
  // Reactively track the marks/nodes active at the current selection.
  const state = useEditorState({
    editor,
    selector: ({ editor }) => ({
      bold: editor.isActive("bold"),
      italic: editor.isActive("italic"),
      underline: editor.isActive("underline"),
      link: editor.isActive("link"),
      bulletList: editor.isActive("bulletList"),
      orderedList: editor.isActive("orderedList"),
    }),
  })

  const setLink = () => {
    const prev = editor.getAttributes("link").href as string | undefined
    const url = window.prompt("Link URL", prev ?? "https://")
    if (url === null) return // cancelled
    if (url === "") {
      editor.chain().focus().extendMarkRange("link").unsetLink().run()
      return
    }
    editor.chain().focus().extendMarkRange("link").setLink({ href: url }).run()
  }

  return (
    <div className="flex flex-wrap items-center gap-0.5 border-b border-gray-950/10 px-1.5 py-1">
      <ToolbarButton title="Bold" active={state.bold} onClick={() => editor.chain().focus().toggleBold().run()}>
        <span className="font-bold">B</span>
      </ToolbarButton>
      <ToolbarButton title="Italic" active={state.italic} onClick={() => editor.chain().focus().toggleItalic().run()}>
        <span className="italic">I</span>
      </ToolbarButton>
      <ToolbarButton
        title="Underline"
        active={state.underline}
        onClick={() => editor.chain().focus().toggleUnderline().run()}
      >
        <span className="underline">U</span>
      </ToolbarButton>
      <ToolbarButton title="Link" active={state.link} onClick={setLink}>
        <span className="underline">🔗</span>
      </ToolbarButton>
      <span className="mx-1 h-4 w-px bg-gray-950/10" />
      <ToolbarButton
        title="Bullet list"
        active={state.bulletList}
        onClick={() => editor.chain().focus().toggleBulletList().run()}
      >
        •
      </ToolbarButton>
      <ToolbarButton
        title="Numbered list"
        active={state.orderedList}
        onClick={() => editor.chain().focus().toggleOrderedList().run()}
      >
        1.
      </ToolbarButton>
    </div>
  )
}

// A basic rich-text editor that emits HTML. The raw-HTML "power mode" lives
// elsewhere; this is the friendly default for composing email bodies.
export const RichTextEditor = React.forwardRef<RichTextEditorHandle, RichTextEditorProps>(
  function RichTextEditor({ value = "", onChange, placeholder, className }, ref) {
    const editor = useEditor({
      immediatelyRender: false,
      extensions: [
        StarterKit.configure({
          heading: false,
          link: {
            openOnClick: false,
            autolink: true,
            HTMLAttributes: { rel: "noopener noreferrer" },
          },
        }),
      ],
      content: value,
      editorProps: {
        attributes: {
          class: "ProseMirror min-h-[9rem] px-3 py-2 text-sm focus:outline-none",
          ...(placeholder ? { "data-placeholder": placeholder } : {}),
        },
      },
      onUpdate: ({ editor }) => {
        onChange?.(cleanHTML(editor.getHTML()))
      },
    })

    // Keep the editor in sync when `value` is replaced from the outside
    // (template insert, wizard reset). Compare against the *cleaned* HTML —
    // what we emit — so normal typing never triggers a setContent, which would
    // reset the document and jump the caret.
    React.useEffect(() => {
      if (!editor) return
      if (value !== cleanHTML(editor.getHTML())) {
        editor.commands.setContent(value || "", { emitUpdate: false })
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [value, editor])

    React.useImperativeHandle(
      ref,
      () => ({
        insertToken: (text: string) => {
          editor?.chain().focus().insertContent(text).run()
        },
        focus: () => {
          editor?.chain().focus().run()
        },
      }),
      [editor]
    )

    return (
      <div
        className={
          "overflow-hidden rounded-md ring-1 ring-black/10 focus-within:ring-2 focus-within:ring-black/20 " +
          (className ?? "")
        }
      >
        {editor ? <Toolbar editor={editor} /> : null}
        <EditorContent editor={editor} />
      </div>
    )
  }
)

export default RichTextEditor
