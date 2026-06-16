import os
import re
import subprocess
import tkinter as tk
from tkinter import messagebox
from version import __version__, APP_NAME

try:
    import customtkinter as ctk
except ImportError as exc:
    raise SystemExit(
        "customtkinter не установлен. Установите пакет командой: pip install customtkinter"
    ) from exc


APP_BG = "#0f141b"
SURFACE = "#151b24"
SURFACE_ALT = "#1b2330"
CARD_BG = "#18212d"
CARD_HOVER = "#202c3b"
CARD_ACTIVE = "#1f6aa5"
BORDER = "#2d394b"
BORDER_ACTIVE = "#66c7ff"
TEXT_PRIMARY = "#f3f6fb"
TEXT_SECONDARY = "#95a4b8"
SUCCESS = "#6fd08c"
ERROR = "#ff7d7d"
WARNING = "#f1c36a"
CARD_WIDTH = 189
CARD_PAD_X = 6
CARD_PAD_Y = 6


ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")


def get_java_versions():
    """
    Возвращает список всех установленных версий Java из update-alternatives --list java.
    """
    versions = []
    seen = set()
    try:
        output = subprocess.check_output(
            ["update-alternatives", "--list", "java"],
            text=True,
        )
        for line in output.strip().splitlines():
            java_bin_path = line.strip()
            java_home = os.path.dirname(os.path.dirname(java_bin_path))
            if java_home and java_home not in seen:
                seen.add(java_home)
                versions.append(java_home)
    except subprocess.CalledProcessError:
        messagebox.showerror(
            "Ошибка",
            "Не удалось получить список Java-версий через update-alternatives.",
        )
    return versions


def get_current_java_system():
    """
    Возвращает системную Java, на которую указывает /usr/bin/java.
    """
    try:
        java_path = subprocess.check_output(
            ["readlink", "-f", "/usr/bin/java"],
            text=True,
        ).strip()
        return os.path.dirname(os.path.dirname(java_path))
    except subprocess.CalledProcessError:
        return None


def read_java_home_from_file(file_path):
    """
    Ищет строку с JAVA_HOME в файле. Возвращает её без 'export '.
    Если нет — возвращает None.
    """
    if os.path.exists(file_path):
        with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
            for line in file:
                if line.startswith("export JAVA_HOME=") or "JAVA_HOME=" in line:
                    return line.strip().replace("export ", "")
    return None


def update_java_home_in_file(file_path, new_java_home):
    """
    Обновляет или добавляет строку 'export JAVA_HOME=...' в файле.
    """
    if not os.path.exists(file_path):
        return

    with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
        lines = file.readlines()

    updated = False
    with open(file_path, "w", encoding="utf-8") as file:
        for line in lines:
            if line.startswith("export JAVA_HOME="):
                file.write(f"export JAVA_HOME={new_java_home}\n")
                updated = True
            else:
                file.write(line)
        if not updated:
            file.write(f"\nexport JAVA_HOME={new_java_home}\n")


def run_sudo_command(command, password, error_prefix, env=None):
    """
    Выполняет sudo-команду, передавая пароль через stdin.
    """
    try:
        subprocess.run(
            ["sudo", "-S", *command],
            input=f"{password}\n",
            text=True,
            check=True,
            capture_output=True,
            env=env,
        )
    except subprocess.CalledProcessError as exc:
        details = (exc.stderr or exc.stdout or str(exc)).strip()
        raise RuntimeError(f"{error_prefix}\n{details}") from exc


def update_environment_file(password, new_java_home):
    """
    Обновляет JAVA_HOME в /etc/environment.
    """
    env = os.environ.copy()
    env["JAVA_HOME_VALUE"] = new_java_home
    run_sudo_command(
        [
            "env",
            f"JAVA_HOME_VALUE={new_java_home}",
            "bash",
            "-c",
            (
                "sed -i '/^JAVA_HOME=/d' /etc/environment && "
                "printf 'JAVA_HOME=\"%s\"\\n' \"$JAVA_HOME_VALUE\" >> /etc/environment"
            ),
        ],
        password,
        "Не удалось изменить /etc/environment:",
        env=env,
    )


def update_alternatives_java(password, new_java_home):
    """
    Меняет системный java через update-alternatives --set java.
    """
    run_sudo_command(
        ["update-alternatives", "--set", "java", f"{new_java_home}/bin/java"],
        password,
        "Не удалось выполнить update-alternatives:",
    )


def ask_sudo_password():
    """
    Показывает модальное окно для ввода sudo-пароля.
    """
    pwd_window = ctk.CTkToplevel(root)
    pwd_window.title("Sudo пароль")
    pwd_window.geometry("420x220")
    pwd_window.resizable(False, False)
    pwd_window.transient(root)
    pwd_window.grab_set()
    pwd_window.configure(fg_color=APP_BG)

    ctk.CTkLabel(
        pwd_window,
        text="Для изменения /etc/environment и системной Java нужны права sudo.",
        font=("Arial", 15, "bold"),
        text_color=TEXT_PRIMARY,
        wraplength=360,
        justify="center",
    ).pack(pady=(24, 10), padx=20)

    ctk.CTkLabel(
        pwd_window,
        text="Пароль будет использован только для текущего применения.",
        font=("Arial", 12),
        text_color=TEXT_SECONDARY,
    ).pack(pady=(0, 14))

    pwd_var = tk.StringVar()
    pwd_entry = ctk.CTkEntry(
        pwd_window,
        textvariable=pwd_var,
        show="*",
        width=320,
        height=40,
        corner_radius=14,
        fg_color=SURFACE_ALT,
        border_color=BORDER,
        text_color=TEXT_PRIMARY,
    )
    pwd_entry.pack(pady=(0, 18))
    pwd_entry.focus_set()

    result = {"password": None}

    def on_ok(event=None):
        result["password"] = pwd_var.get().strip()
        pwd_window.grab_release()
        pwd_window.destroy()

    def on_cancel():
        pwd_window.grab_release()
        pwd_window.destroy()

    pwd_window.bind("<Return>", on_ok)
    pwd_window.bind("<KP_Enter>", on_ok)
    pwd_entry.bind("<Return>", on_ok)
    pwd_entry.bind("<KP_Enter>", on_ok)

    buttons = ctk.CTkFrame(pwd_window, fg_color="transparent")
    buttons.pack()

    ctk.CTkButton(
        buttons,
        text="Отмена",
        width=120,
        height=38,
        corner_radius=14,
        fg_color=SURFACE_ALT,
        hover_color=CARD_HOVER,
        border_width=1,
        border_color=BORDER,
        command=on_cancel,
    ).pack(side="left", padx=6)

    ctk.CTkButton(
        buttons,
        text="Применить",
        width=120,
        height=38,
        corner_radius=14,
        fg_color=CARD_ACTIVE,
        hover_color="#2b79b7",
        command=on_ok,
    ).pack(side="left", padx=6)

    pwd_window.wait_window()
    return result["password"] or None


def set_busy_state(is_busy):
    root.configure(cursor="watch" if is_busy else "")
    root.update_idletasks()


def switch_java(new_java_home):
    """
    Выполняет переключение JAVA_HOME в выбранных местах.
    """
    if not new_java_home:
        messagebox.showerror("Ошибка", "Выберите версию Java.")
        return False

    do_bashrc = var_bashrc.get()
    do_profile = var_profile.get()
    do_environment = var_environment.get()
    do_alternatives = var_alternatives.get()

    set_busy_state(True)
    try:
        if do_bashrc:
            update_java_home_in_file(os.path.expanduser("~/.bashrc"), new_java_home)

        if do_profile:
            update_java_home_in_file(os.path.expanduser("~/.profile"), new_java_home)

        if do_environment or do_alternatives:
            password = ask_sudo_password()
            if not password:
                messagebox.showwarning("Отмена", "Пароль не введён. Операция прервана.")
                return False

            if do_environment:
                update_environment_file(password, new_java_home)

            if do_alternatives:
                update_alternatives_java(password, new_java_home)

        os.environ["JAVA_HOME"] = new_java_home
        selected_java_home.set(new_java_home)
        update_ui()
        refresh_version_cards()
        return True
    except RuntimeError as error:
        messagebox.showerror("Ошибка", str(error))
        return False
    finally:
        set_busy_state(False)


def format_value(value):
    if value is None:
        return "Не найдено", ERROR
    return value, SUCCESS


def update_status_label(widget, value):
    text_value, color = format_value(value)
    widget.configure(text=text_value, text_color=color)


def update_ui():
    """
    Обновляет информацию о текущей системной Java и содержимом файлов.
    """
    current_system = get_current_java_system()
    current_system_value = current_system or "Не удалось определить"
    lbl_current_system.configure(
        text=current_system_value,
        text_color=TEXT_PRIMARY if current_system else WARNING,
    )

    update_status_label(
        lbl_bashrc,
        read_java_home_from_file(os.path.expanduser("~/.bashrc")),
    )
    update_status_label(
        lbl_profile,
        read_java_home_from_file(os.path.expanduser("~/.profile")),
    )
    update_status_label(
        lbl_env,
        read_java_home_from_file("/etc/environment"),
    )


def parse_java_display(java_home):
    normalized_path = java_home.rstrip("/")
    path_parts = normalized_path.split("/")
    base_name = path_parts[-1] if path_parts else normalized_path
    suffix_parts = []

    if base_name == "jre" and len(path_parts) > 1:
        suffix_parts.append("jre")
        base_name = path_parts[-2]

    match = re.match(r"^((?:java|jdk)-\d+(?:\.\d+)*)(?:-(.+))?$", base_name)
    if match:
        title = match.group(1)
        subtitle_parts = []
        if match.group(2):
            subtitle_parts.append(match.group(2))
        if suffix_parts:
            subtitle_parts.extend(suffix_parts)
        subtitle = "/".join(subtitle_parts)
        return title, subtitle, java_home

    fallback_parts = base_name.split("-", 2)
    title = "-".join(fallback_parts[:2]) if len(fallback_parts) >= 2 else base_name
    subtitle = fallback_parts[2] if len(fallback_parts) == 3 else ""
    if suffix_parts:
        subtitle = f"{subtitle}/{'/'.join(suffix_parts)}".strip("/")
    return title, subtitle, java_home


class JavaCard(ctk.CTkFrame):
    def __init__(self, master, java_home, on_select):
        super().__init__(
            master,
            width=CARD_WIDTH,
            height=114,
            corner_radius=18,
            fg_color=CARD_BG,
            border_width=1,
            border_color=BORDER,
        )
        self.grid_propagate(False)
        self.java_home = java_home
        self.on_select = on_select
        self.is_selected = False
        title, subtitle, address = parse_java_display(java_home)

        self.indicator = ctk.CTkLabel(
            self,
            text="",
            width=24,
            height=24,
            corner_radius=12,
            fg_color=SURFACE_ALT,
            text_color=TEXT_PRIMARY,
            font=("Arial", 13, "bold"),
        )
        self.indicator.grid(row=0, column=1, padx=(6, 10), pady=(10, 0), sticky="ne")

        self.title_label = ctk.CTkLabel(
            self,
            text=title,
            font=("Arial", 14),
            text_color=TEXT_PRIMARY,
            anchor="w",
        )
        self.title_label.grid(row=0, column=0, padx=(10, 6), pady=(10, 2), sticky="nw")

        self.subtitle_label = ctk.CTkLabel(
            self,
            text=subtitle or " ",
            font=("Arial", 12, "bold"),
            text_color=TEXT_PRIMARY,
            anchor="w",
        )
        self.subtitle_label.grid(row=1, column=0, columnspan=2, padx=10, pady=(0, 2), sticky="nw")

        self.path_label = ctk.CTkLabel(
            self,
            text=address,
            font=("Arial", 10),
            text_color=TEXT_SECONDARY,
            justify="left",
            anchor="w",
            wraplength=135,
        )
        self.path_label.grid(row=2, column=0, columnspan=2, padx=10, pady=(0, 8), sticky="nsew")

        self.columnconfigure(0, weight=1)
        self.bind_click(self)
        self.bind_click(self.indicator)
        self.bind_click(self.title_label)
        self.bind_click(self.subtitle_label)
        self.bind_click(self.path_label)

        self.bind("<Enter>", self.on_enter)
        self.bind("<Leave>", self.on_leave)
        self.indicator.bind("<Enter>", self.on_enter)
        self.indicator.bind("<Leave>", self.on_leave)
        self.title_label.bind("<Enter>", self.on_enter)
        self.title_label.bind("<Leave>", self.on_leave)
        self.subtitle_label.bind("<Enter>", self.on_enter)
        self.subtitle_label.bind("<Leave>", self.on_leave)
        self.path_label.bind("<Enter>", self.on_enter)
        self.path_label.bind("<Leave>", self.on_leave)

    def bind_click(self, widget):
        widget.bind("<Button-1>", lambda _event: self.on_select(self.java_home))

    def on_enter(self, _event):
        if not self.is_selected:
            self.configure(fg_color=CARD_HOVER)

    def on_leave(self, _event):
        if not self.is_selected:
            self.configure(fg_color=CARD_BG)

    def set_selected(self, is_selected):
        self.is_selected = is_selected
        if is_selected:
            self.configure(fg_color=CARD_ACTIVE, border_color=BORDER_ACTIVE)
            self.indicator.configure(text="✓", fg_color=BORDER_ACTIVE, text_color=APP_BG)
            self.subtitle_label.configure(text_color=TEXT_PRIMARY)
            self.path_label.configure(text_color="#d7ebff")
        else:
            self.configure(fg_color=CARD_BG, border_color=BORDER)
            self.indicator.configure(text="", fg_color=SURFACE_ALT, text_color=TEXT_PRIMARY)
            self.subtitle_label.configure(text_color=TEXT_PRIMARY)
            self.path_label.configure(text_color=TEXT_SECONDARY)


def handle_version_select(java_home):
    switch_java(java_home)


def scroll_versions(units):
    canvas = versions_frame._parent_canvas
    if canvas.yview() != (0.0, 1.0):
        canvas.yview_scroll(units, "units")


def on_versions_mousewheel(event):
    if getattr(event, "num", None) == 4:
        scroll_versions(-1)
        return "break"
    if getattr(event, "num", None) == 5:
        scroll_versions(1)
        return "break"

    delta = getattr(event, "delta", 0)
    if delta == 0:
        return None

    if abs(delta) >= 120:
        units = -int(delta / 120)
    else:
        units = -1 if delta > 0 else 1

    scroll_versions(units)
    return "break"


def bind_versions_mousewheel(widget):
    widget.bind("<MouseWheel>", on_versions_mousewheel, add="+")
    widget.bind("<Button-4>", on_versions_mousewheel, add="+")
    widget.bind("<Button-5>", on_versions_mousewheel, add="+")


def refresh_version_cards():
    selected = selected_java_home.get()
    for card in version_cards:
        if isinstance(card, JavaCard):
            card.set_selected(card.java_home == selected)


def get_versions_columns():
    available_width = versions_frame._parent_canvas.winfo_width()
    if available_width <= 1:
        available_width = 760

    column_width = CARD_WIDTH + CARD_PAD_X * 2
    columns = max(1, available_width // column_width)
    return min(max(1, len(all_versions)), columns)


def build_version_cards():
    global current_versions_columns

    for card in version_cards:
        card.destroy()
    version_cards.clear()

    for column in range(8):
        cards_container.grid_columnconfigure(column, weight=0, uniform="")

    if not all_versions:
        empty_label = ctk.CTkLabel(
            cards_container,
            text="Установленные версии Java не найдены.",
            text_color=WARNING,
            font=("Arial", 14),
        )
        empty_label.grid(row=0, column=0, padx=10, pady=12, sticky="w")
        bind_versions_mousewheel(empty_label)
        version_cards.append(empty_label)
        return

    columns = get_versions_columns()
    current_versions_columns = columns
    for column in range(columns):
        cards_container.grid_columnconfigure(column, weight=1, uniform="jdk_cards")

    for index, java_home in enumerate(all_versions):
        row = index // columns
        column = index % columns
        card = JavaCard(cards_container, java_home, handle_version_select)
        card.grid(row=row, column=column, padx=CARD_PAD_X, pady=CARD_PAD_Y, sticky="n")
        bind_versions_mousewheel(card)
        bind_versions_mousewheel(card.indicator)
        bind_versions_mousewheel(card.title_label)
        bind_versions_mousewheel(card.subtitle_label)
        bind_versions_mousewheel(card.path_label)
        version_cards.append(card)

    refresh_version_cards()


root = ctk.CTk()
root.title(f"{APP_NAME} v{__version__}")
root.geometry("920x720")
root.minsize(820, 640)
root.configure(fg_color=APP_BG)

current_system_java = get_current_java_system()
all_versions = get_java_versions()
if current_system_java and current_system_java not in all_versions:
    all_versions.insert(0, current_system_java)

selected_java_home = tk.StringVar(value=current_system_java or (all_versions[0] if all_versions else ""))

var_bashrc = tk.BooleanVar(value=True)
var_profile = tk.BooleanVar(value=True)
var_environment = tk.BooleanVar(value=True)
var_alternatives = tk.BooleanVar(value=True)

main_frame = ctk.CTkFrame(root, corner_radius=0, fg_color=APP_BG)
main_frame.pack(fill="both", expand=True, padx=16, pady=16)

versions_section = ctk.CTkFrame(main_frame, corner_radius=24, fg_color=SURFACE)
versions_section.pack(fill="both", expand=True, pady=(0, 12))

ctk.CTkLabel(
    versions_section,
    text="Доступные JDK",
    font=("Arial", 20, "bold"),
    text_color=TEXT_PRIMARY,
).pack(anchor="w", padx=16, pady=(14, 8))
versions_frame = ctk.CTkScrollableFrame(
    versions_section,
    corner_radius=18,
    fg_color=SURFACE_ALT,
    border_width=1,
    border_color=BORDER,
    scrollbar_button_color=CARD_ACTIVE,
    scrollbar_button_hover_color="#2b79b7",
)
versions_frame.pack(fill="both", expand=True, padx=12, pady=(0, 12))
bind_versions_mousewheel(versions_frame)
bind_versions_mousewheel(versions_frame._parent_canvas)

cards_container = ctk.CTkFrame(versions_frame, fg_color="transparent")
cards_container.pack(fill="x", padx=4, pady=4)
bind_versions_mousewheel(cards_container)

version_cards = []
current_versions_columns = None
build_version_cards()


def on_versions_resize(_event):
    if not all_versions:
        return

    new_columns = get_versions_columns()
    if new_columns != current_versions_columns:
        build_version_cards()


versions_frame._parent_canvas.bind("<Configure>", on_versions_resize, add="+")

options_frame = ctk.CTkFrame(main_frame, corner_radius=24, fg_color=SURFACE)
options_frame.pack(fill="x", pady=(0, 12))

ctk.CTkLabel(
    options_frame,
    text="Куда применять выбор",
    font=("Arial", 20, "bold"),
    text_color=TEXT_PRIMARY,
).pack(anchor="w", padx=16, pady=(14, 10))

checks_grid = ctk.CTkFrame(options_frame, fg_color="transparent")
checks_grid.pack(fill="x", padx=12, pady=(0, 12))
checks_grid.grid_columnconfigure(0, weight=1)
checks_grid.grid_columnconfigure(1, weight=1)

option_card_kwargs = {
    "corner_radius": 16,
    "fg_color": SURFACE_ALT,
    "border_width": 1,
    "border_color": BORDER,
}

check_kwargs = {
    "checkbox_width": 22,
    "checkbox_height": 22,
    "corner_radius": 8,
    "fg_color": CARD_ACTIVE,
    "hover_color": "#2b79b7",
    "border_color": BORDER_ACTIVE,
    "text_color": TEXT_PRIMARY,
    "font": ("Arial", 14),
}

status_label_kwargs = {
    "font": ("Arial", 11),
    "justify": "left",
    "anchor": "w",
    "wraplength": 300,
}

bashrc_card = ctk.CTkFrame(checks_grid, **option_card_kwargs)
bashrc_card.grid(row=0, column=0, sticky="nsew", padx=4, pady=4)

ctk.CTkCheckBox(
    bashrc_card,
    text="Обновить ~/.bashrc",
    variable=var_bashrc,
    **check_kwargs,
).pack(anchor="w", padx=10, pady=(10, 4))

lbl_bashrc = ctk.CTkLabel(bashrc_card, text="", **status_label_kwargs)
lbl_bashrc.pack(fill="x", padx=10, pady=(0, 10))

profile_card = ctk.CTkFrame(checks_grid, **option_card_kwargs)
profile_card.grid(row=0, column=1, sticky="nsew", padx=4, pady=4)

ctk.CTkCheckBox(
    profile_card,
    text="Обновить ~/.profile",
    variable=var_profile,
    **check_kwargs,
).pack(anchor="w", padx=10, pady=(10, 4))

lbl_profile = ctk.CTkLabel(profile_card, text="", **status_label_kwargs)
lbl_profile.pack(fill="x", padx=10, pady=(0, 10))

environment_card = ctk.CTkFrame(checks_grid, **option_card_kwargs)
environment_card.grid(row=1, column=0, sticky="nsew", padx=4, pady=4)

ctk.CTkCheckBox(
    environment_card,
    text="Обновить /etc/environment (sudo)",
    variable=var_environment,
    **check_kwargs,
).pack(anchor="w", padx=10, pady=(10, 4))

lbl_env = ctk.CTkLabel(environment_card, text="", **status_label_kwargs)
lbl_env.pack(fill="x", padx=10, pady=(0, 10))

alternatives_card = ctk.CTkFrame(checks_grid, **option_card_kwargs)
alternatives_card.grid(row=1, column=1, sticky="nsew", padx=4, pady=4)

ctk.CTkCheckBox(
    alternatives_card,
    text="Сменить системную Java (sudo)",
    variable=var_alternatives,
    **check_kwargs,
).pack(anchor="w", padx=10, pady=(10, 4))

lbl_current_system = ctk.CTkLabel(
    alternatives_card,
    text="",
    font=("Arial", 11),
    text_color=TEXT_SECONDARY,
    justify="left",
    anchor="w",
    wraplength=300,
)
lbl_current_system.pack(fill="x", padx=10, pady=(0, 10))

update_ui()

root.mainloop()
