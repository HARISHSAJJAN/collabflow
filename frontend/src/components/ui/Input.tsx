import { type InputHTMLAttributes, type TextareaHTMLAttributes, forwardRef } from "react";
import { cn } from "../../lib/cn";

interface FieldWrapperProps {
  label?: string;
  error?: string;
  hint?: string;
}

const fieldBase =
  "w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-3.5 py-2.5 text-sm text-[var(--text-primary)] " +
  "placeholder:text-[var(--text-tertiary)] outline-none transition-shadow duration-150 " +
  "focus:border-brand-500 focus:ring-2 focus:ring-brand-500/20";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & FieldWrapperProps>(
  ({ className, label, error, hint, id, ...props }, ref) => {
    const fieldId = id ?? label?.toLowerCase().replace(/\s+/g, "-");
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label htmlFor={fieldId} className="text-sm font-medium text-[var(--text-secondary)]">
            {label}
          </label>
        )}
        <input ref={ref} id={fieldId} className={cn(fieldBase, error && "border-red-500 focus:border-red-500 focus:ring-red-500/20", className)} {...props} />
        {error ? <span className="text-xs text-red-500">{error}</span> : hint ? <span className="text-xs text-[var(--text-tertiary)]">{hint}</span> : null}
      </div>
    );
  },
);
Input.displayName = "Input";

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & FieldWrapperProps>(
  ({ className, label, error, hint, id, ...props }, ref) => {
    const fieldId = id ?? label?.toLowerCase().replace(/\s+/g, "-");
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label htmlFor={fieldId} className="text-sm font-medium text-[var(--text-secondary)]">
            {label}
          </label>
        )}
        <textarea ref={ref} id={fieldId} className={cn(fieldBase, "resize-none", error && "border-red-500", className)} {...props} />
        {error ? <span className="text-xs text-red-500">{error}</span> : hint ? <span className="text-xs text-[var(--text-tertiary)]">{hint}</span> : null}
      </div>
    );
  },
);
Textarea.displayName = "Textarea";

export const Select = forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement> & FieldWrapperProps>(
  ({ className, label, error, id, children, ...props }, ref) => {
    const fieldId = id ?? label?.toLowerCase().replace(/\s+/g, "-");
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label htmlFor={fieldId} className="text-sm font-medium text-[var(--text-secondary)]">
            {label}
          </label>
        )}
        <select ref={ref} id={fieldId} className={cn(fieldBase, "cursor-pointer", className)} {...props}>
          {children}
        </select>
        {error && <span className="text-xs text-red-500">{error}</span>}
      </div>
    );
  },
);
Select.displayName = "Select";
