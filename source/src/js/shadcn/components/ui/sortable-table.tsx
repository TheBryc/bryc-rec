import * as React from "react"
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
  DragStartEvent,
} from "@dnd-kit/core"
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { restrictToVerticalAxis } from "@dnd-kit/modifiers"
import { GripVertical } from "lucide-react"

import { cn } from "@/shadcn/lib/utils"

// Context for passing drag listeners to DragHandleCell
const DragListenersContext = React.createContext<Record<string, any> | null>(null)

// ---- SortableTableProvider ----
// Wraps DndContext + SortableContext. Place OUTSIDE <table> to avoid
// DndContext's accessibility <div> appearing inside the <table>.
interface SortableTableProviderProps {
  items: string[]
  onReorder: (oldIndex: number, newIndex: number) => void
  children: React.ReactNode
}

function SortableTableProvider({
  items,
  onReorder,
  children,
}: SortableTableProviderProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 5 },
    }),
    useSensor(KeyboardSensor)
  )

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (over && active.id !== over.id) {
      const oldIndex = items.indexOf(String(active.id))
      const newIndex = items.indexOf(String(over.id))
      if (oldIndex !== -1 && newIndex !== -1) {
        onReorder(oldIndex, newIndex)
      }
    }
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      modifiers={[restrictToVerticalAxis]}
      onDragEnd={handleDragEnd}
    >
      <SortableContext items={items} strategy={verticalListSortingStrategy}>
        {children}
      </SortableContext>
    </DndContext>
  )
}

// ---- SortableTableRow ----
interface SortableTableRowProps extends React.ComponentProps<"tr"> {
  id: string
  disabled?: boolean
}

function SortableTableRow({
  id,
  disabled = false,
  className,
  children,
  ...props
}: SortableTableRowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id, disabled })

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    position: "relative",
    zIndex: isDragging ? 1 : undefined,
  }

  return (
    <DragListenersContext.Provider value={disabled ? null : (listeners ?? null)}>
      <tr
        ref={setNodeRef}
        style={style}
        data-slot="table-row"
        className={cn(
          "hover:bg-muted/50 data-[state=selected]:bg-muted border-b transition-colors",
          isDragging && "bg-muted/80",
          className
        )}
        {...attributes}
        {...props}
      >
        {children}
      </tr>
    </DragListenersContext.Provider>
  )
}

// ---- DragHandleCell ----
interface DragHandleCellProps extends React.ComponentProps<"td"> {}

function DragHandleCell({ className, ...props }: DragHandleCellProps) {
  const listeners = React.useContext(DragListenersContext)

  return (
    <td
      data-slot="table-cell"
      className={cn("p-2 align-middle w-10", className)}
      {...props}
    >
      {listeners ? (
        <div
          className="flex items-center justify-center h-7 w-7 cursor-grab active:cursor-grabbing text-muted-foreground hover:text-foreground"
          {...listeners}
          onClick={(e) => e.stopPropagation()}
        >
          <GripVertical className="h-4 w-4" />
        </div>
      ) : null}
    </td>
  )
}

export { SortableTableProvider, SortableTableRow, DragHandleCell }
