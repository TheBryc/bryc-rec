"use client"

import * as React from "react"
import { CalendarIcon } from "lucide-react"
import { format, parse, isValid } from "date-fns"

import { cn } from "@/shadcn/lib/utils"
import { Button } from "@/shadcn/components/ui/button"
import { Calendar } from "@/shadcn/components/ui/calendar"
import { Input } from "@/shadcn/components/ui/input"
import { Label } from "@/shadcn/components/ui/label"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/shadcn/components/ui/popover"

interface DateTimePickerProps {
  value?: string // datetime-local format: YYYY-MM-DDTHH:mm
  onChange?: (value: string) => void
  placeholder?: string
  fromYear?: number
  toYear?: number
  className?: string
  disabled?: boolean
}

function DateTimePicker({
  value,
  onChange,
  placeholder = "Pick date & time",
  fromYear = 2020,
  toYear = new Date().getFullYear() + 1,
  className,
  disabled = false,
}: DateTimePickerProps) {
  const [open, setOpen] = React.useState(false)

  // Parse datetime-local string (YYYY-MM-DDTHH:mm) into 12h parts
  const { selectedDate, hour12, minutes, period } = React.useMemo(() => {
    if (!value || value === "") {
      return { selectedDate: undefined, hour12: "12", minutes: "00", period: "PM" as const }
    }
    const [datePart, timePart] = value.split("T")
    const parsed = parse(datePart, "yyyy-MM-dd", new Date())
    const [hStr, m] = (timePart || "12:00").split(":")
    const h24 = parseInt(hStr || "12", 10)
    return {
      selectedDate: isValid(parsed) ? parsed : undefined,
      hour12: String(h24 === 0 ? 12 : h24 > 12 ? h24 - 12 : h24),
      minutes: m || "00",
      period: (h24 >= 12 ? "PM" : "AM") as "AM" | "PM",
    }
  }, [value])

  // Convert 12h + period back to 24h and emit
  const emitChange = (date: Date | undefined, h12: string, m: string, p: "AM" | "PM") => {
    if (!date || !onChange) return
    const datePart = format(date, "yyyy-MM-dd")
    let h24 = parseInt(h12, 10)
    if (p === "AM" && h24 === 12) h24 = 0
    else if (p === "PM" && h24 !== 12) h24 += 12
    const hh = String(h24).padStart(2, "0")
    const mm = m.padStart(2, "0")
    onChange(`${datePart}T${hh}:${mm}`)
  }

  const handleDateSelect = (date: Date | undefined) => {
    if (date) {
      emitChange(date, hour12, minutes, period)
    }
  }

  const handleHoursChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    const num = parseInt(val, 10)
    if (val === "" || (num >= 1 && num <= 12)) {
      emitChange(selectedDate, val === "" ? "12" : String(num), minutes, period)
    }
  }

  const handleMinutesChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    const num = parseInt(val, 10)
    if (val === "" || (num >= 0 && num <= 59)) {
      emitChange(selectedDate, hour12, val === "" ? "0" : String(num), period)
    }
  }

  const togglePeriod = () => {
    const newPeriod = period === "AM" ? "PM" : "AM"
    emitChange(selectedDate, hour12, minutes, newPeriod)
  }

  // Format for display in the trigger button
  const displayValue = React.useMemo(() => {
    if (!selectedDate) return null
    return `${format(selectedDate, "MMM d, yyyy")} at ${hour12}:${minutes.padStart(2, "0")} ${period}`
  }, [selectedDate, hour12, minutes, period])

  const defaultMonth = selectedDate ?? new Date()

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          disabled={disabled}
          className={cn(
            "w-full justify-start text-left font-normal",
            !selectedDate && "text-muted-foreground",
            className
          )}
        >
          <CalendarIcon className="mr-2 h-4 w-4" />
          {displayValue ? displayValue : <span>{placeholder}</span>}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={selectedDate}
          onSelect={handleDateSelect}
          defaultMonth={defaultMonth}
          captionLayout="dropdown"
          fromYear={fromYear}
          toYear={toYear}
          initialFocus
        />
        <div className="border-t px-3 py-3">
          <Label className="text-xs text-muted-foreground">Time</Label>
          <div className="flex items-center gap-1.5 mt-1.5">
            <Input
              type="number"
              min={1}
              max={12}
              value={hour12}
              onChange={handleHoursChange}
              className="w-16 text-center"
              disabled={disabled}
            />
            <span className="text-sm font-medium text-muted-foreground">:</span>
            <Input
              type="number"
              min={0}
              max={59}
              value={minutes.padStart(2, "0")}
              onChange={handleMinutesChange}
              className="w-16 text-center"
              disabled={disabled}
            />
            <Button
              variant="outline"
              size="sm"
              type="button"
              className="ml-1 w-14 text-xs"
              disabled={disabled}
              onClick={togglePeriod}
            >
              {period}
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

export { DateTimePicker }
