"use client"

import * as React from "react"
import { CalendarIcon } from "lucide-react"
import { format, parse, isValid } from "date-fns"

import { cn } from "@/shadcn/lib/utils"
import { Button } from "@/shadcn/components/ui/button"
import { Calendar } from "@/shadcn/components/ui/calendar"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/shadcn/components/ui/popover"

interface DatePickerProps {
  value?: string // ISO format: YYYY-MM-DD
  onChange?: (value: string) => void
  onBlur?: () => void
  placeholder?: string
  fromYear?: number
  toYear?: number
  className?: string
}

function DatePicker({
  value,
  onChange,
  onBlur,
  placeholder = "Pick a date",
  fromYear = 1990,
  toYear = new Date().getFullYear(),
  className,
}: DatePickerProps) {
  const [open, setOpen] = React.useState(false)

  // Parse ISO date string to Date object
  const selectedDate = React.useMemo(() => {
    if (!value || value === "") return undefined
    const parsed = parse(value, "yyyy-MM-dd", new Date())
    return isValid(parsed) ? parsed : undefined
  }, [value])

  // Default month for calendar
  const defaultMonth = selectedDate ?? new Date(Math.floor((fromYear + toYear) / 2), 0, 1)

  const handleSelect = (date: Date | undefined) => {
    if (date && onChange) {
      // Format as ISO date string
      onChange(format(date, "yyyy-MM-dd"))
    }
    setOpen(false)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          className={cn(
            "w-full justify-start text-left font-normal",
            !selectedDate && "text-muted-foreground",
            className
          )}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {selectedDate ? format(selectedDate, "PPP") : <span>{placeholder}</span>}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={selectedDate}
          onSelect={handleSelect}
          defaultMonth={defaultMonth}
          captionLayout="dropdown"
          fromYear={fromYear}
          toYear={toYear}
          initialFocus
        />
      </PopoverContent>
    </Popover>
  )
}

export { DatePicker }
