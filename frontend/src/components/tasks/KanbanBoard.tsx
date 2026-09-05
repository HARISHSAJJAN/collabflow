import { useState } from "react";
import { DndContext, type DragEndEvent, DragOverlay, type DragStartEvent, PointerSensor, useSensor, useSensors } from "@dnd-kit/core";
import { KanbanColumn } from "./KanbanColumn";
import { TaskCard } from "./TaskCard";
import { useChangeTaskStatus } from "../../hooks/useTasks";
import { TASK_STATUSES } from "../../lib/types";
import type { ProjectMemberResponse, TaskResponse, TaskStatus } from "../../lib/types";

export function KanbanBoard({
  projectId,
  tasks,
  members,
  onTaskClick,
  onAddTask,
}: {
  projectId: string;
  tasks: TaskResponse[];
  members?: ProjectMemberResponse[];
  onTaskClick: (task: TaskResponse) => void;
  onAddTask: () => void;
}) {
  const changeStatus = useChangeTaskStatus(projectId);
  const [activeTask, setActiveTask] = useState<TaskResponse | null>(null);
  // A small drag activation distance, not an instant drag-on-mousedown: without it, a plain
  // click on a card (to open its details) would sometimes register as a zero-distance drag
  // instead, since useDraggable's listeners are spread onto the whole card.
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }));

  const byStatus = (status: TaskStatus) => tasks.filter((t) => t.status === status);

  const handleDragStart = (event: DragStartEvent) => {
    setActiveTask(tasks.find((t) => t.id === event.active.id) ?? null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveTask(null);
    const { active, over } = event;
    if (!over) return;
    const task = tasks.find((t) => t.id === active.id);
    const newStatus = over.id as TaskStatus;
    if (task && task.status !== newStatus) {
      changeStatus.mutate({ taskId: task.id, status: newStatus });
    }
  };

  return (
    <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
      {/* h-full here is load-bearing, not decorative: without it this row collapses to its
          content height, each column's flex-1 droppable area has nothing to grow against, and
          an empty/short column ends up with a droppable zone only ~96px (min-h-24) tall -
          found by an automated drag-and-drop test that dropped a card 100px below a column
          header and missed the actual droppable area entirely.

          select-none on the whole board, not just on each draggable card: found by the same
          automated test dragging a card and having the browser instead perform a native text
          selection that swept across other columns' "No tasks" labels. TaskCard already had
          its own select-none, but that only stops *that* text from being selected - it can't
          stop the browser's selection gesture from starting in the first place and then
          extending across *other*, unrelated text as the pointer moves, since the gesture
          begins before dnd-kit's PointerSensor has confirmed a drag past its activation
          distance (the exact window a per-card, drag-state-conditional select-none still
          missed - trying that first and watching it still fail is what pinned this down to a
          timing gap, not a targeting gap). A Kanban board's cards are manipulated by dragging
          and by the detail drawer, never by selecting their raw text, so disabling selection
          here has no real cost. */}
      <div className="flex h-full select-none gap-4 overflow-x-auto scrollbar-thin px-6 pb-6">
        {TASK_STATUSES.map((status) => (
          <KanbanColumn key={status} status={status} tasks={byStatus(status)} members={members} onTaskClick={onTaskClick} onAddTask={onAddTask} />
        ))}
      </div>
      <DragOverlay>{activeTask && <div className="w-72 rotate-1"><TaskCard task={activeTask} members={members} onClick={() => {}} /></div>}</DragOverlay>
    </DndContext>
  );
}
