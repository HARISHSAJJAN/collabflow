import { avatarColor, initials } from "../../lib/display";
import { cn } from "../../lib/cn";

interface AvatarProps {
  name: string;
  size?: "xs" | "sm" | "md" | "lg";
  className?: string;
}

const sizeClasses = {
  xs: "size-5 text-[10px]",
  sm: "size-7 text-xs",
  md: "size-9 text-sm",
  lg: "size-14 text-lg",
};

export function Avatar({ name, size = "md", className }: AvatarProps) {
  return (
    <div
      title={name}
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full font-semibold text-white ring-2 ring-[var(--bg-surface)]",
        sizeClasses[size],
        avatarColor(name),
        className,
      )}
    >
      {initials(name || "?")}
    </div>
  );
}

export function AvatarStack({ names, max = 4 }: { names: string[]; max?: number }) {
  const shown = names.slice(0, max);
  const overflow = names.length - shown.length;
  return (
    <div className="flex -space-x-2">
      {shown.map((name, i) => (
        <Avatar key={name + i} name={name} size="sm" />
      ))}
      {overflow > 0 && (
        <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-[var(--bg-surface-2)] text-xs font-semibold text-[var(--text-secondary)] ring-2 ring-[var(--bg-surface)]">
          +{overflow}
        </div>
      )}
    </div>
  );
}
