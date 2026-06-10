import { changeMapType } from "@/features/todayTodos/query/reorder-todo";
import { TodoItemTypeWithDateChecksum } from "@/features/todayTodos/query/update-todo";
import { TodoItemType } from "@/types";
import { QueryStatus, UseMutateFunction } from "@tanstack/react-query";
import React, { createContext, useContext } from "react";

// hook types
type UseDeleteTodoType = () => {
    deleteMutateFn: (variables: TodoItemType) => void;
    deletePending: boolean;
}
type UseCompleteTodoType = () => {
    completeMutateFn: UseMutateFunction<void, Error, TodoItemType, { oldTodos: TodoItemType[]; }>;
    completePending: boolean
}
type UsePrioritizeTodoType = () => {
    prioritizeMutateFn: UseMutateFunction<void, Error, { id: string; level: "Low" | "Medium" | "High"; isRecurring: boolean; }, { oldTodos: TodoItemType[] | undefined; }>;
    prioritizePending: boolean
}

type UseReorderTodoType = () => {
    reorderMutateFn: UseMutateFunction<void, Error, changeMapType[], unknown>;
    reorderPending: boolean
}

type UseEditTodoType = () => {
    editTodoMutateFn: UseMutateFunction<void, Error, TodoItemTypeWithDateChecksum, { oldTodos: TodoItemType[] | undefined; }>
    editTodoStatus: QueryStatus | "idle"
}

type UseEditTodoInstanceType = (setEditInstanceOnly: React.Dispatch<React.SetStateAction<boolean>> | undefined) => {
    editTodoInstanceMutateFn: UseMutateFunction<void, Error, TodoItemType, { oldTodos: TodoItemType[] | undefined; }>
    editTodoInstanceStatus: QueryStatus | "idle"
}

type TodoMutaionProviderProps = {
    useDeleteTodo: UseDeleteTodoType,
    useCompleteTodo: UseCompleteTodoType,
    usePrioritizeTodo: UsePrioritizeTodoType,
    useReorderTodo: UseReorderTodoType,
    useEditTodoInstance: UseEditTodoInstanceType,
    useEditTodo: UseEditTodoType,
    // True when the current screen shows a shared list where the user is a
    // VIEWER — task rows hide their mutation affordances.
    readOnly?: boolean,
    children: React.ReactNode
}

const TodoMutationContext = createContext<Omit<TodoMutaionProviderProps, "children"> | null>(null)


export default function TodoMutationProvider({ useDeleteTodo, useCompleteTodo, usePrioritizeTodo, useReorderTodo, useEditTodo, useEditTodoInstance, readOnly = false, children }: TodoMutaionProviderProps) {
    return (
        <TodoMutationContext.Provider value={{ useDeleteTodo, useCompleteTodo, usePrioritizeTodo, useReorderTodo, useEditTodo, useEditTodoInstance, readOnly }}>
            {children}
        </TodoMutationContext.Provider>
    )
}

export function useTodoMutation() {
    const context = useContext(TodoMutationContext)
    if (!context) throw new Error('useTodoMutation must be used within TodoMutationProvider');
    return context
}
