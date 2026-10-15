package com.Lilith.FMusic.server.core.objs.message;

/**
 * 消息模板集合。
 *
 * <p>
 * 文本<b>不再</b>来自 fmusic_server/message.json: 每条消息只持有 i18n key 与参数
 * (见 {@link Msg}), 服务端发出 translate 组件, 由各客户端按自己的语言渲染。
 * 需要纯文本的场景 (HUD 数据组装等) 用 {@link Msg#resolve()} 在服务端解析 (英文)。
 */
public class MessageObj {

    public MusicPlayObj musicPlay = MusicPlayObj.make();
    public AddMusicObj addMusic = AddMusicObj.make();
    public PageObj page = PageObj.make();
    public LyricObj lyric = LyricObj.make();
    public VoteObj vote = VoteObj.make();
    public PushObj push = PushObj.make();
    public SearchObj search = SearchObj.make();
    public HudObj hud = HudObj.make();
    public HudNameObj hudList = HudNameObj.make();
    public CommandObj command = CommandObj.make();
    public CustomObj custom = CustomObj.make();
    public PAPIObj papi = PAPIObj.make();
    public CostObj cost = CostObj.make();
    public ClickObj click = ClickObj.make();
    public FunObj fun = FunObj.make();
    public CancelObj cancel = CancelObj.make();

    public static MessageObj make() {
        return new MessageObj();
    }
}
