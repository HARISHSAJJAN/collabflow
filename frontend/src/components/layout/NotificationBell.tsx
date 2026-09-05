import { useState, useRef, useEffect } from "react";
import { Bell, CheckCheck } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { useMarkAllNotificationsRead, useMarkNotificationRead, useNotifications, useUnreadCount } from "../../hooks/useNotifications";
import { NOTIFICATION_COPY } from "../../lib/display";
import { relativeTime } from "../../lib/display";
import { Spinner } from "../ui/Badge";

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { data: unread } = useUnreadCount();
  const { data: notifications, isLoading } = useNotifications();
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();

  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  const count = unread?.unreadCount ?? 0;

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative rounded-lg p-2 text-[var(--text-secondary)] transition-colors hover:bg-[var(--bg-surface-2)] hover:text-[var(--text-primary)]"
        aria-label="Notifications"
      >
        <Bell className="size-5" />
        {count > 0 && (
          <span className="absolute right-1 top-1 flex size-4 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white">
            {count > 9 ? "9+" : count}
          </span>
        )}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.15 }}
            className="absolute right-0 top-12 z-40 w-80 overflow-hidden rounded-xl border border-[var(--border-subtle)] bg-[var(--bg-surface)] shadow-xl"
          >
            <div className="flex items-center justify-between border-b border-[var(--border-subtle)] px-4 py-3">
              <span className="text-sm font-semibold">Notifications</span>
              {count > 0 && (
                <button onClick={() => markAllRead.mutate()} className="flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700">
                  <CheckCheck className="size-3.5" /> Mark all read
                </button>
              )}
            </div>
            <div className="max-h-96 overflow-y-auto scrollbar-thin">
              {isLoading ? (
                <Spinner className="py-8" />
              ) : !notifications?.content.length ? (
                <p className="px-4 py-8 text-center text-sm text-[var(--text-tertiary)]">You're all caught up.</p>
              ) : (
                notifications.content.map((n) => (
                  <button
                    key={n.id}
                    onClick={() => !n.read && markRead.mutate(n.id)}
                    className="flex w-full flex-col gap-0.5 border-b border-[var(--border-subtle)] px-4 py-3 text-left transition-colors last:border-0 hover:bg-[var(--bg-surface-2)]"
                  >
                    <span className={`text-sm ${n.read ? "text-[var(--text-secondary)]" : "font-medium text-[var(--text-primary)]"}`}>
                      {NOTIFICATION_COPY[n.type]}
                      {!n.read && <span className="ml-2 inline-block size-1.5 rounded-full bg-brand-500 align-middle" />}
                    </span>
                    <span className="text-xs text-[var(--text-tertiary)]">{relativeTime(n.createdAt)}</span>
                  </button>
                ))
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
