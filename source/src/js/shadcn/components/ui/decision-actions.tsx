"use client"

import * as React from "react"
import { Check, X, CheckCircle2, XCircle } from "lucide-react"
import { Button } from "@/shadcn/components/ui/button"
import { cn } from "@/shadcn/lib/utils"

interface DecisionActionsProps {
  decision: "approved" | "rejected" | null
  onApprove: () => void
  onReject: () => void
  size?: "sm" | "default"
  className?: string
}

export function DecisionActions({
  decision,
  onApprove,
  onReject,
  size = "sm",
  className,
}: DecisionActionsProps) {
  // Decision made - show final status icon (no undo)
  if (decision === "approved") {
    return (
      <div className={cn("flex items-center justify-center", className)}>
        <CheckCircle2 className="h-5 w-5 text-green-600" />
      </div>
    )
  }

  if (decision === "rejected") {
    return (
      <div className={cn("flex items-center justify-center", className)}>
        <XCircle className="h-5 w-5 text-red-600" />
      </div>
    )
  }

  // No decision yet - show action buttons
  return (
    <div className={cn("flex items-center gap-1", className)}>
      <Button
        variant="ghost"
        size={size}
        onClick={(e) => {
          e.stopPropagation()
          onApprove()
        }}
        className="h-7 w-7 p-0 hover:bg-green-100 hover:text-green-700"
      >
        <Check className="h-4 w-4" />
      </Button>
      <Button
        variant="ghost"
        size={size}
        onClick={(e) => {
          e.stopPropagation()
          onReject()
        }}
        className="h-7 w-7 p-0 hover:bg-red-100 hover:text-red-700"
      >
        <X className="h-4 w-4" />
      </Button>
    </div>
  )
}
