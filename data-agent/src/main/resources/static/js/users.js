/**
 * 用户管理页逻辑（独立页面）。
 *
 * 依赖：
 *   - common.js 暴露的 window.DA
 *   - Vue 3 通过 CDN 引入
 *
 * 功能：
 *   - 用户列表（分页 + 关键字搜索）
 *   - 新增 / 编辑用户（含角色多选 + 部门树多选）
 *   - 重置密码
 *   - 删除用户（保留至少一个 admin）
 */
const { createApp, ref, computed, onMounted } = Vue;

// 递归部门树节点组件（嵌套自身）
let DeptNode = {
    name: 'DeptNode',
    props: {
        dept: { type: Object, required: true },
        selectedIds: { type: Array, required: true }
    },
    setup(props, { expose }) {
        const expanded = ref(true);
        const hasChildren = computed(() =>
            Array.isArray(props.dept.children) && props.dept.children.length > 0
        );

        const isChecked = computed(() => props.selectedIds.includes(props.dept.id));

        const toggleCheck = (e) => {
            // v-model 不好直接用在跨组件，这里手动同步父组件的 selectedIds
            // 通过原生 checkbox 的 change 事件 + 自定义事件
            const checked = e.target.checked;
            const idx = props.selectedIds.indexOf(props.dept.id);
            if (checked && idx === -1) {
                props.selectedIds.push(props.dept.id);
            } else if (!checked && idx !== -1) {
                props.selectedIds.splice(idx, 1);
            }
        };

        return { expanded, hasChildren, isChecked, toggleCheck };
    },
    template: `
        <div>
            <div class="da-dept-node">
                <span class="da-dept-toggle"
                      :class="{ expanded: hasChildren && expanded, empty: !hasChildren }"
                      @click="hasChildren && (expanded = !expanded)">
                    <i class="fas fa-chevron-right"></i>
                </span>
                <input type="checkbox" :checked="isChecked" @change="toggleCheck" />
                <span>{{ dept.name }}</span>
            </div>
            <div v-if="hasChildren && expanded" class="da-dept-children">
                <dept-node v-for="child in dept.children" :key="child.id"
                           :dept="child" :selected-ids="selectedIds"></dept-node>
            </div>
        </div>
    `
};

createApp({
    components: { DeptNode },
    setup() {
        // ==================== 列表状态 ====================
        const users = ref([]);
        const loading = ref(false);        // 初次加载（整张表显示骨架/占位）
        const page = ref(1);
        const size = ref(20);
        const total = ref(0);
        const totalPages = ref(0);
        const searchKeyword = ref('');
        const appliedKeyword = ref(''); // 实际生效的搜索关键字
        let searchDebounceTimer = null;

        // ==================== 弹窗状态 ====================
        const formVisible = ref(false);
        const editingUser = ref(null);
        const formSaving = ref(false);
        const allRoles = ref([]);
        const allDepts = ref([]);

        const resetVisible = ref(false);
        const resetSaving = ref(false);
        const resetTarget = ref(null);

        // ==================== 通用确认弹窗 ====================
        // 替代原生 confirm()，保持设计系统一致。
        // 调用方式：const ok = await showConfirm({ title, body, hint, tone, okText, icon });
        const confirmVisible = ref(false);
        const confirmState = ref({
            title: '',
            body: '',
            hint: '',
            tone: 'warn',          // warn / danger / success / info
            okText: '确认',
            icon: 'fas fa-triangle-exclamation'
        });
        let confirmResolver = null;
        const confirmMaskDownOnMask = ref(false);

        const showConfirm = (opts = {}) => {
            return new Promise((resolve) => {
                confirmState.value = {
                    title: opts.title || '确认操作',
                    body: opts.body || '',
                    hint: opts.hint || '',
                    tone: opts.tone || 'warn',
                    okText: opts.okText || '确认',
                    icon: opts.icon || 'fas fa-triangle-exclamation'
                };
                confirmResolver = resolve;
                confirmVisible.value = true;
            });
        };

        const onConfirmOk = () => {
            confirmVisible.value = false;
            if (confirmResolver) {
                confirmResolver(true);
                confirmResolver = null;
            }
        };

        const onConfirmCancel = () => {
            confirmVisible.value = false;
            if (confirmResolver) {
                confirmResolver(false);
                confirmResolver = null;
            }
        };

        const onConfirmMaskMouseDown = (e) => {
            confirmMaskDownOnMask.value = (e.target === e.currentTarget);
        };
        const onConfirmMaskClick = (e) => {
            if (e.target === e.currentTarget && confirmMaskDownOnMask.value) {
                onConfirmCancel();
            }
            confirmMaskDownOnMask.value = false;
        };

        // 密码可见性切换（新增用户、重置密码弹窗各自独立）
        const showFormPassword = ref(false);
        const showResetNew = ref(false);
        const showResetConfirm = ref(false);

        // 弹窗遮罩点击关闭辅助状态：
        // 只有 mousedown 落在遮罩本身（不是内部卡片）时，click 才允许触发关闭。
        // 否则用户在弹窗内按住鼠标拖到遮罩上松开会误关弹窗。
        const formMaskDownOnMask = ref(false);
        const resetMaskDownOnMask = ref(false);

        const onFormMaskMouseDown = (e) => {
            formMaskDownOnMask.value = (e.target === e.currentTarget);
        };
        const onFormMaskClick = (e) => {
            if (e.target === e.currentTarget && formMaskDownOnMask.value) {
                closeForm();
            }
            formMaskDownOnMask.value = false;
        };
        const onResetMaskMouseDown = (e) => {
            resetMaskDownOnMask.value = (e.target === e.currentTarget);
        };
        const onResetMaskClick = (e) => {
            if (e.target === e.currentTarget && resetMaskDownOnMask.value) {
                closeReset();
            }
            resetMaskDownOnMask.value = false;
        };

        const defaultForm = () => ({
            username: '',
            password: '',
            nickname: '',
            status: 'ACTIVE',
            roleIds: [],
            deptIds: [],
            profile: { realName: '', idCard: '', age: null, education: '', homeAddress: '' }
        });

        const form = ref(defaultForm());

        const resetForm = ref({
            userId: null,
            newPassword: '',
            confirmPassword: ''
        });

        // ==================== 加载用户列表 ====================
        const loadUsers = async () => {
            // 统一显示全表 loading（首次 + 搜索 + 翻页都一样）
            loading.value = true;
            try {
                const params = {
                    page: page.value,
                    size: size.value
                };
                if (appliedKeyword.value) {
                    params.keyword = appliedKeyword.value;
                }
                const body = await DA.apiGet('/sys/user/list', params);
                if (body && body.code === 200 && body.data) {
                    users.value = body.data.records || [];
                    total.value = body.data.total || 0;
                    totalPages.value = body.data.pages || 0;
                } else {
                    users.value = [];
                    total.value = 0;
                    totalPages.value = 0;
                }
            } catch (e) {
                console.error('[users] 加载失败', e);
                DA.showToast(e.message || '加载失败');
                users.value = [];
            } finally {
                loading.value = false;
            }
        };

        // ==================== 搜索 / 分页 ====================
        const onSearch = () => {
            appliedKeyword.value = searchKeyword.value.trim();
            page.value = 1;
            loadUsers();
        };

        /**
         * 输入框防抖搜索：停顿 300ms 后自动触发，不替换表格不刷页面。
         * 立即回车或点搜索按钮则立即触发。
         */
        const onSearchInput = () => {
            if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
            searchDebounceTimer = setTimeout(() => {
                searchDebounceTimer = null;
                const kw = searchKeyword.value.trim();
                // 跟当前生效关键字相同就不重复请求
                if (kw === appliedKeyword.value) return;
                appliedKeyword.value = kw;
                page.value = 1;
                loadUsers();
            }, 300);
        };

        const onReset = () => {
            if (searchDebounceTimer) {
                clearTimeout(searchDebounceTimer);
                searchDebounceTimer = null;
            }
            searchKeyword.value = '';
            appliedKeyword.value = '';
            page.value = 1;
            loadUsers();
        };

        const changePage = (newPage) => {
            if (newPage < 1 || newPage > totalPages.value) return;
            page.value = newPage;
            loadUsers();
        };

        // ==================== 加载角色 + 部门（表单下拉用）====================
        const loadRolesAndDepts = async () => {
            try {
                const [roleBody, deptBody] = await Promise.all([
                    DA.apiGet('/sys/role/list'),
                    DA.apiGet('/sys/dept/tree')
                ]);
                if (roleBody && roleBody.code === 200) {
                    allRoles.value = roleBody.data || [];
                }
                if (deptBody && deptBody.code === 200) {
                    allDepts.value = deptBody.data || [];
                }
            } catch (e) {
                console.warn('[users] 加载角色/部门失败', e);
            }
        };

        // ==================== 新增 / 编辑 ====================
        const openCreate = async () => {
            editingUser.value = null;
            form.value = defaultForm();
            showFormPassword.value = false;
            await loadRolesAndDepts();
            formVisible.value = true;
        };

        const openEdit = async (user) => {
            editingUser.value = user;
            const p = user.profile || {};
            form.value = {
                username: user.username || '',
                password: user.password || '',
                nickname: user.nickname || '',
                status: user.status || 'ACTIVE',
                roleIds: (user.roles || []).map(r => r.id),
                deptIds: (user.depts || []).map(d => d.id),
                profile: {
                    realName: p.realName || '',
                    idCard: p.idCard || '',
                    age: p.age != null ? p.age : null,
                    education: p.education || '',
                    homeAddress: p.homeAddress || ''
                }
            };
            showFormPassword.value = false;
            await loadRolesAndDepts();
            formVisible.value = true;
        };

        const closeForm = () => {
            formVisible.value = false;
            editingUser.value = null;
        };

        const onSubmitForm = async () => {
            // 参数校验
            if (!editingUser.value) {
                if (!form.value.username.trim()) {
                    DA.showToast('请输入用户名');
                    return;
                }
            }
            if (!form.value.password) {
                DA.showToast('请输入密码');
                return;
            }

            formSaving.value = true;
            try {
                const payload = {
                    username: form.value.username.trim(),
                    password: form.value.password,
                    nickname: form.value.nickname.trim(),
                    roleIds: form.value.roleIds,
                    deptIds: form.value.deptIds,
                    profile: {
                        realName: form.value.profile.realName.trim(),
                        idCard: (form.value.profile.idCard || '').trim(),
                        age: form.value.profile.age === '' || form.value.profile.age == null
                            ? null : Number(form.value.profile.age),
                        education: form.value.profile.education || null,
                        homeAddress: (form.value.profile.homeAddress || '').trim() || null
                    }
                };
                // 新增用户固定 ACTIVE，编辑时不传 status（由专门的 toggle 接口切换）
                if (!editingUser.value) {
                    payload.status = 'ACTIVE';
                }

                if (editingUser.value) {
                    await DA.apiPut('/sys/user/' + editingUser.value.id, payload);
                    DA.showToast('更新成功');
                } else {
                    await DA.apiPost('/sys/user', payload);
                    DA.showToast('新增成功');
                }
                closeForm();
                await loadUsers();
            } catch (e) {
                console.error('[users] 保存失败', e);
                DA.showToast(e.message || '保存失败');
            } finally {
                formSaving.value = false;
            }
        };

        // ==================== 启用/禁用状态切换 ====================
        const onToggleStatus = async (user) => {
            const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
            const verb = next === 'ACTIVE' ? '启用' : '禁用';
            const tone = next === 'ACTIVE' ? 'success' : 'warn';
            const icon = next === 'ACTIVE' ? 'fas fa-circle-check' : 'fas fa-ban';
            const ok = await showConfirm({
                title: `确认${verb}用户`,
                body: `用户「${user.nickname || user.username}」`,
                hint: next === 'DISABLED' ? '禁用后该用户将立即下线，无法再登录或访问系统' : '启用后该用户可正常登录',
                tone,
                okText: verb,
                icon
            });
            if (!ok) return;
            try {
                await DA.apiPut('/sys/user/' + user.id + '/status?status=' + next, {});
                DA.showToast(verb + '成功');
                await loadUsers();
            } catch (e) {
                console.error('[users] 切换状态失败', e);
                DA.showToast(e.message || '操作失败');
            }
        };

        // ==================== 重置密码 ====================
        const openResetPassword = (user) => {
            resetTarget.value = user;
            resetForm.value = {
                userId: user.id,
                newPassword: '',
                confirmPassword: ''
            };
            showResetNew.value = false;
            showResetConfirm.value = false;
            resetVisible.value = true;
        };

        const closeReset = () => {
            resetVisible.value = false;
            resetTarget.value = null;
        };

        const onSubmitReset = async () => {
            if (!resetForm.value.newPassword) {
                DA.showToast('请输入新密码');
                return;
            }
            if (resetForm.value.newPassword !== resetForm.value.confirmPassword) {
                DA.showToast('两次输入的密码不一致');
                return;
            }
            resetSaving.value = true;
            try {
                await DA.apiPost('/sys/user/reset-password', {
                    userId: resetForm.value.userId,
                    newPassword: resetForm.value.newPassword
                });
                DA.showToast('重置成功');
                closeReset();
            } catch (e) {
                console.error('[users] 重置密码失败', e);
                DA.showToast(e.message || '重置失败');
            } finally {
                resetSaving.value = false;
            }
        };

        // ==================== 删除 ====================
        const onDelete = async (user) => {
            const ok = await showConfirm({
                title: '确认删除用户',
                body: `用户「${user.nickname || user.username}」`,
                hint: '该操作会级联删除其角色 / 部门关联，且不可恢复',
                tone: 'danger',
                okText: '删除',
                icon: 'fas fa-trash-can'
            });
            if (!ok) return;
            try {
                await DA.apiDelete('/sys/user/' + user.id);
                DA.showToast('删除成功');
                // 如果删除的是当前页最后一条，回退一页
                if (users.value.length === 1 && page.value > 1) {
                    page.value--;
                }
                await loadUsers();
            } catch (e) {
                console.error('[users] 删除失败', e);
                DA.showToast(e.message || '删除失败');
            }
        };

        // ==================== 工具 ====================
        const formatTime = (iso) => {
            if (!iso) return '—';
            try {
                const d = new Date(iso);
                return d.toLocaleString('zh-CN', {
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit'
                });
            } catch (e) {
                return iso;
            }
        };

        // 角色 data_scope 中文说明（新增用户时辅助选角色）
        const DATA_SCOPE_LABELS = {
            ALL: '全部数据',
            DEPT_AND_SUB: '本部门及子部门',
            DEPT: '仅本部门',
            SELF: '仅本人'
        };

        const dataScopeLabel = (scope) => DATA_SCOPE_LABELS[scope] || '—';

        const dataScopeHint = (scope) => {
            const map = {
                ALL: '可看所有数据，无任何限制',
                DEPT_AND_SUB: '可看自己所在部门及其下属部门的数据',
                DEPT: '只能看自己所在部门的数据',
                SELF: '只能看自己创建的数据'
            };
            return map[scope] || '';
        };

        // ==================== 生命周期 ====================
        onMounted(async () => {
            // 鉴权守卫：非 admin 跳回主页
            const user = await DA.requireAdmin();
            if (!user) return;

            // 渲染顶栏
            const headerHost = document.getElementById('da-header-host');
            if (headerHost) {
                headerHost.innerHTML = DA.renderHeader({ active: 'users', user: user });
                DA.bindHeaderEvents();
            }

            loadUsers();
        });

        return {
            users,
            loading,
            page,
            total,
            totalPages,
            searchKeyword,
            formVisible,
            editingUser,
            formSaving,
            allRoles,
            allDepts,
            form,
            showFormPassword,
            resetVisible,
            resetSaving,
            resetTarget,
            resetForm,
            showResetNew,
            showResetConfirm,
            confirmVisible,
            confirmState,
            onSearch,
            onSearchInput,
            onReset,
            changePage,
            openCreate,
            openEdit,
            closeForm,
            onSubmitForm,
            onFormMaskMouseDown,
            onFormMaskClick,
            openResetPassword,
            closeReset,
            onSubmitReset,
            onResetMaskMouseDown,
            onResetMaskClick,
            onToggleStatus,
            onDelete,
            onConfirmOk,
            onConfirmCancel,
            onConfirmMaskMouseDown,
            onConfirmMaskClick,
            formatTime,
            dataScopeLabel,
            dataScopeHint
        };
    }
}).mount('#da-users-app');
