import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { CalendarCheck, ListTodo } from "lucide-react";
import { taskApi } from "../lib/endpoints";
import { useCurrentUser } from "../hooks/useAuth";
import { Card, EmptyState, Spinner } from "../components/ui/Badge";
import { Badge } from "../components/ui/Badge";
import { PRIORITY_META, STATUS_META } from "../lib/display";

export function DashboardPage() {
  const { data: me } = useCurrentUser();
  const { data: myTasks, isLoading } = useQuery({ queryKey: ["tasks", "me"], queryFn: () => taskApi.myAssigned() });

  const greeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return "Good morning";
    if (hour < 18) return "Good afternoon";
    return "Good evening";
  };

  return (
    <div className="mx-auto max-w-4xl px-6 py-8">
      <h1 className="text-2xl font-bold text-[var(--text-primary)]">
        {greeting()}
        {me ? `, ${me.fullName.split(" ")[0]}` : ""}
      </h1>
      <p className="mt-1 text-sm text-[var(--text-secondary)]">Here's everything assigned to you, across every project.</p>

      <div className="mt-8">
        {isLoading ? (
          <Spinner className="py-16" />
        ) : !myTasks?.content.length ? (
          <EmptyState
            icon={<ListTodo className="size-8" />}
            title="Nothing assigned to you right now"
            description="Tasks assigned to you in any project will show up here."
          />
        ) : (
          <div className="flex flex-col gap-2.5">
            {myTasks.content.map((task) => (
              <Link key={task.id} to={`/projects/${task.projectId}`}>
                <Card className="flex items-center justify-between p-4 transition-transform hover:-translate-y-0.5 hover:shadow-md">
                  <div className="flex items-center gap-3">
                    <span className={`size-2 rounded-full ${STATUS_META[task.status].dot}`} />
                    <div>
                      <p className="font-medium text-[var(--text-primary)]">{task.title}</p>
                      <p className="text-xs text-[var(--text-tertiary)]">{STATUS_META[task.status].label}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    {task.dueDate && (
                      <span className="flex items-center gap-1 text-xs text-[var(--text-tertiary)]">
                        <CalendarCheck className="size-3.5" />
                        {new Date(task.dueDate).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
                      </span>
                    )}
                    <Badge className={PRIORITY_META[task.priority].className}>{PRIORITY_META[task.priority].label}</Badge>
                  </div>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
