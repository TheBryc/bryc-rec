import * as React from "react"
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
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

// Context for passing drag listeners to DragHandle
const DragListenersContext = React.createContext<Record<string, any> | null>(null)

// ---- SortableListProvider ----
// Wraps DndContext + SortableContext for div-based sortable lists.
interface SortableListProviderProps {
  items: string[]
  onReorder: (oldIndex: number, newIndex: number) => void
  children: React.ReactNode
}

function SortableListProvider({
  items,
  onReorder,
  children,
}: SortableListProviderProps) {
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

// ---- SortableListItem ----
interface SortableListItemProps extends React.ComponentProps<"div"> {
  id: string
  disabled?: boolean
}

function SortableListItem({
  id,
  disabled = false,
  className,
  children,
  ...props
}: SortableListItemProps) {
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
      <div
        ref={setNodeRef}
        style={style}
        className={cn(isDragging && "bg-muted/80 rounded-md", className)}
        {...attributes}
        {...props}
      >
        {children}
      </div>
    </DragListenersContext.Provider>
  )
}

// ---- DragHandle ----
interface DragHandleProps extends React.ComponentProps<"div"> {}

function DragHandle({ className, ...props }: DragHandleProps) {
  const listeners = React.useContext(DragListenersContext)

  return listeners ? (
    <div
      className={cn(
        "flex items-center justify-center h-7 w-7 flex-shrink-0 cursor-grab active:cursor-grabbing text-muted-foreground hover:text-foreground",
        className
      )}
      {...listeners}
      {...props}
      onClick={(e) => e.stopPropagation()}
    >
      <GripVertical className="h-4 w-4" />
    </div>
  ) : null
}

export { SortableListProvider, SortableListItem, DragHandle }
