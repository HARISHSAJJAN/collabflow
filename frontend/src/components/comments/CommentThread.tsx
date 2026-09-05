import { useState } from "react";
import { Check, Pencil, Trash2, X } from "lucide-react";
import { useAddComment, useComments, useDeleteComment, useUpdateComment } from "../../hooks/useComments";
import { useCurrentUser } from "../../hooks/useAuth";
import { Avatar } from "../ui/Avatar";
import { Button } from "../ui/Button";
import { Textarea } from "../ui/Input";
import { Spinner } from "../ui/Badge";
import { relativeTime } from "../../lib/display";

export function CommentThread({ taskId }: { taskId: string }) {
  const { data: comments, isLoading } = useComments(taskId);
  const { data: me } = useCurrentUser();
  const addComment = useAddComment(taskId);
  const updateComment = useUpdateComment(taskId);
  const deleteComment = useDeleteComment(taskId);
  const [draft, setDraft] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!draft.trim()) return;
    addComment.mutate(draft.trim(), { onSuccess: () => setDraft("") });
  };

  const saveEdit = (commentId: string) => {
    if (!editDraft.trim()) return;
    updateComment.mutate({ commentId, body: editDraft.trim() }, { onSuccess: () => setEditingId(null) });
  };

  return (
    <div className="flex flex-col gap-4">
      {isLoading ? (
        <Spinner className="py-6" />
      ) : (
        <div className="flex flex-col gap-4">
          {comments?.content.length === 0 && <p className="text-sm text-[var(--text-tertiary)]">No comments yet - be the first to say something.</p>}
          {comments?.content.map((c) => (
            <div key={c.id} className="flex gap-3">
              <Avatar name={c.authorName} size="sm" className="mt-0.5" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold">{c.authorName}</span>
                  <span className="text-xs text-[var(--text-tertiary)]">
                    {relativeTime(c.createdAt)}
                    {c.edited && " (edited)"}
                  </span>
                </div>
                {editingId === c.id ? (
                  <div className="mt-1.5 flex flex-col gap-2">
                    <Textarea value={editDraft} onChange={(e) => setEditDraft(e.target.value)} rows={2} autoFocus />
                    <div className="flex gap-2">
                      <Button size="sm" onClick={() => saveEdit(c.id)} loading={updateComment.isPending}>
                        <Check className="size-3.5" /> Save
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}>
                        <X className="size-3.5" /> Cancel
                      </Button>
                    </div>
                  </div>
                ) : (
                  <p className="mt-0.5 whitespace-pre-wrap break-words text-sm text-[var(--text-secondary)]">{c.body}</p>
                )}
                {me?.id === c.authorId && editingId !== c.id && (
                  <div className="mt-1 flex gap-3">
                    <button
                      onClick={() => {
                        setEditingId(c.id);
                        setEditDraft(c.body);
                      }}
                      className="flex items-center gap-1 text-xs text-[var(--text-tertiary)] hover:text-[var(--text-primary)]"
                    >
                      <Pencil className="size-3" /> Edit
                    </button>
                    <button onClick={() => deleteComment.mutate(c.id)} className="flex items-center gap-1 text-xs text-[var(--text-tertiary)] hover:text-red-500">
                      <Trash2 className="size-3" /> Delete
                    </button>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={submit} className="flex gap-3 border-t border-[var(--border-subtle)] pt-4">
        {me && <Avatar name={me.fullName} size="sm" className="mt-0.5" />}
        <div className="flex-1">
          <Textarea placeholder="Write a comment..." rows={2} value={draft} onChange={(e) => setDraft(e.target.value)} />
          <div className="mt-2 flex justify-end">
            <Button type="submit" size="sm" loading={addComment.isPending} disabled={!draft.trim()}>
              Comment
            </Button>
          </div>
        </div>
      </form>
    </div>
  );
}
